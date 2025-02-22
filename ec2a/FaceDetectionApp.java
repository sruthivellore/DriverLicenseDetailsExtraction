package com.aws.ec2a;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import java.util.*;

public class FaceDetectionApp {

    public static void main(String[] args) {
        String s3BucketName = "cs643-sp25-project1";
        String fifoQueueName = "imginfo.fifo"; // FIFO queue name for ordered message processing
        String messageGroupId = "group1"; // Group for messages in FIFO queue
        System.out.println("---------------------------------------------------");
        System.out.println("Face Detection Application");
        System.out.println("---------------------------------------------------");
        
        // Initialize S3, Rekognition, and SQS service clients
        S3Client s3Client = S3Client.builder()
                .region(Region.US_EAST_1)
                .build();
        RekognitionClient rekognitionClient = RekognitionClient.builder()
                .region(Region.US_EAST_1)
                .build();
        SqsClient sqsClient = SqsClient.builder()
                .region(Region.US_EAST_1)
                .build();
        System.out.println("Connecting to S3, Rekognition, and SQS services...");
        System.out.println("Fetching image list from S3 bucket: " + s3BucketName);
        // Process images in the specified S3 bucket
        processImagesInBucket(s3Client, rekognitionClient, sqsClient, s3BucketName, fifoQueueName, messageGroupId);
    }

    public static void processImagesInBucket(S3Client s3Client, RekognitionClient rekognitionClient, SqsClient sqsClient, 
                                              String s3BucketName, String fifoQueueName, String messageGroupId) {
    	

        // URL of the FIFO queue to send messages
        String queueUrl = "https://sqs.us-east-1.amazonaws.com/610922412536/imginfo.fifo";
        try {
            // Check if the queue exists or needs to be created
            ListQueuesRequest listQueuesRequest = ListQueuesRequest.builder()
                    .queueNamePrefix(fifoQueueName)
                    .build();
            ListQueuesResponse listQueuesResponse = sqsClient.listQueues(listQueuesRequest);

            if (listQueuesResponse.queueUrls().size() == 0) {
                // If the queue doesn't exist, create it
                CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
                        .attributesWithStrings(Map.of("FifoQueue", "true", "ContentBasedDeduplication", "true"))
                        .queueName(fifoQueueName)
                        .build();
                sqsClient.createQueue(createQueueRequest);

                // Get the queue URL after creation
                GetQueueUrlRequest getQueueUrlRequest = GetQueueUrlRequest.builder()
                        .queueName(fifoQueueName)
                        .build();
                queueUrl = sqsClient.getQueueUrl(getQueueUrlRequest).queueUrl();
            } else {
                // If the queue exists, use the existing URL
                queueUrl = listQueuesResponse.queueUrls().get(0);
            }
        } catch (QueueNameExistsException e) {
            throw e;
        }
        System.out.println("---------------------------------------------------");
  	  	System.out.println("Analyzing Images for face using AWS Rekognition...");
        System.out.println("---------------------------------------------------");
        // Process up to 10 images from the S3 bucket
        try {
            // List objects in the S3 bucket (limit to 10 objects)
            ListObjectsV2Request listObjectsRequest = ListObjectsV2Request.builder().bucket(s3BucketName).maxKeys(10)
                    .build();
            ListObjectsV2Response listObjectsResponse = s3Client.listObjectsV2(listObjectsRequest);

            // Loop through the images in the S3 bucket
            for (S3Object s3Object : listObjectsResponse.contents()) {
                System.out.println("Processing image from S3 bucket: " + s3Object.key());

                // Prepare the image for Rekognition analysis
                Image image = Image.builder().s3Object(software.amazon.awssdk.services.rekognition.model.S3Object
                                .builder().bucket(s3BucketName).name(s3Object.key()).build())
                        .build();
                DetectLabelsRequest detectLabelsRequest = DetectLabelsRequest.builder().image(image).minConfidence((float) 75)
                        .build();
                DetectLabelsResponse detectLabelsResponse = rekognitionClient.detectLabels(detectLabelsRequest);
                List<Label> labels = detectLabelsResponse.labels();

                // Check if the image contains a "Face" label and send to SQS if found
                for (Label label : labels) {
                    if (label.name().equals("Face")) {
                        sqsClient.sendMessage(SendMessageRequest.builder().messageGroupId(messageGroupId).queueUrl(queueUrl)
                                .messageBody(s3Object.key()).build());
                        break;
                    }
                }
            }
            System.out.println("---------------------------------------------------");
      	  	System.out.println("All images processed and sent image name to SQS FIFO queue.");
            System.out.println("---------------------------------------------------");
            // Indicate the end of image processing by sending a termination message to the queue
            sqsClient.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageGroupId(messageGroupId).messageBody("-1")
                    .build());
        } catch (Exception e) {
            // Handle any exceptions that occur during processing
            System.err.println("Error: " + e.getLocalizedMessage());
            System.exit(1);
        }
    }
}