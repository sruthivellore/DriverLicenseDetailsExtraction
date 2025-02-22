package com.aws.ec2b;


import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import java.util.*;
import java.io.*;

public class TextExtractionApp {

    public static void main(String[] args) {
        // Specify the S3 bucket and FIFO queue names
        String s3Bucket = "cs643-sp25-project1";
        String fifoQueue = "imginfo.fifo";

        // Initialize S3, Rekognition, and SQS service clients
        System.out.println("---------------------------------------------------");
        System.out.println("Text Extraction Application");
        System.out.println("---------------------------------------------------");
        S3Client s3Client = S3Client.builder()
                .region(Region.US_EAST_1)
                .build();
        RekognitionClient rekognitionClient = RekognitionClient.builder()
                .region(Region.US_EAST_1)
                .build();
        SqsClient sqsClient = SqsClient.builder()
                .region(Region.US_EAST_1)
                .build();

        // Begin processing images from the S3 bucket
        processCarImages(s3Client, rekognitionClient, sqsClient, s3Bucket, fifoQueue);
    }

    public static void processCarImages(S3Client s3Client, RekognitionClient rekognitionClient, SqsClient sqsClient, 
                                        String s3Bucket, String fifoQueue) {

        // Poll the SQS queue to check if it's ready for message processing (created by DetectCars)
        boolean queueExists = false;
        while (!queueExists) {
            ListQueuesRequest listQueuesRequest = ListQueuesRequest.builder()
                    .queueNamePrefix(fifoQueue)
                    .build();
            ListQueuesResponse listQueuesResponse = sqsClient.listQueues(listQueuesRequest);
            if (listQueuesResponse.queueUrls().size() > 0)
                queueExists = true;
        }

        // Retrieve the URL of the SQS queue
        String queueUrl = "";
        try {
            GetQueueUrlRequest getQueueRequest = GetQueueUrlRequest.builder()
                    .queueName(fifoQueue)
                    .build();
            queueUrl = sqsClient.getQueueUrl(getQueueRequest)
                    .queueUrl();
        } catch (QueueNameExistsException e) {
            throw e;
        }

        // Process images containing car-related text
        try {
            boolean endOfQueue = false;
            HashMap<String, String> extractedTexts = new HashMap<>();

            while (!endOfQueue) {
                // Retrieve the next message from the queue
                ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder().queueUrl(queueUrl)
                        .maxNumberOfMessages(1).build();
                List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).messages();

                if (messages.size() > 0) {
                    Message message = messages.get(0);
                    String imageName = message.body();

                    if (imageName.equals("-1")) {
                        // When processing completes, instance A sends "-1" to signal no more images
                        endOfQueue = true;
                    } else {
                        System.out.println("Processing the image with text from S3 bucket: " + imageName);

                        // Prepare the image for text detection
                        Image image = Image.builder().s3Object(S3Object.builder().bucket(s3Bucket).name(imageName).build())
                                .build();
                        DetectTextRequest detectTextRequest = DetectTextRequest.builder()
                                .image(image)
                                .build();
                        DetectTextResponse detectTextResponse = rekognitionClient.detectText(detectTextRequest);
                        List<TextDetection> textDetections = detectTextResponse.textDetections();

                        // If text is detected, store it in the outputs map
                        if (textDetections.size() != 0) {
                            String detectedText = "";
                            for (TextDetection textDetection : textDetections) {
                                if (textDetection.type().equals(TextTypes.WORD))
                                    detectedText = detectedText.concat(" " + textDetection.detectedText());
                            }
                            extractedTexts.put(imageName, detectedText);
                        }
                    }

                    // Delete the processed message from the queue
                    DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder().queueUrl(queueUrl)
                            .receiptHandle(message.receiptHandle())
                            .build();
                    sqsClient.deleteMessage(deleteMessageRequest);
                }
            }

            // Write the extracted texts to an output file
            try {
                FileWriter fileWriter = new FileWriter("output.txt");

                Iterator<Map.Entry<String, String>> iterator = extractedTexts.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, String> entry = iterator.next();
                    fileWriter.write(entry.getKey() + ": " + entry.getValue() + "\n");
                    iterator.remove();
                }

                fileWriter.close();
                System.out.println("---------------------------------------------------");
                System.out.println("All extracted text has been saved to output.txt");
                System.out.println("---------------------------------------------------");
            } catch (IOException e) {
                System.out.println("An error occurred while writing to the file.");
                e.printStackTrace();
            }
        } catch (Exception e) {
            // Handle any unexpected errors
            System.err.println("Error: " + e.getLocalizedMessage());
            System.exit(1);
        }
    }
}
