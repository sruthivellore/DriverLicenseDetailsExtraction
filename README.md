## DriverLicenseDetailsExtraction

A cloud-based pipeline for automated face and text extraction from driver license images using AWS Rekognition, Textract, S3, SQS, and EC2.

---

## Overview

DriverLicenseDetailsExtraction is a Java-based application that automates the extraction of facial and textual information from images of driver licenses. It leverages AWS cloud services for scalable, efficient, and accurate processing. The system is designed to process large batches of images, identify those containing both a face and text, and output the extracted details for downstream use.

---

## Architecture

The solution is built on a distributed AWS architecture involving two EC2 instances:

- **EC2 Instance A:** Handles face detection using AWS Rekognition.
- **EC2 Instance B:** Handles text extraction using AWS Textract.

Communication and task coordination between the instances are managed via an SQS FIFO queue. Images are stored and retrieved from an S3 bucket.

**Workflow:**
1. EC2 A fetches images from S3 and uses Rekognition to detect faces.
2. If a face is detected (confidence > 75%), the image index is sent to SQS.
3. EC2 B reads image indexes from SQS, extracts text using Textract, and writes results to an output file.
4. Only images containing both face and text are included in the final output.

---

## Features

- **Automated face detection** using AWS Rekognition.
- **Text extraction** from images using AWS Textract.
- **Scalable and parallel processing** via separate EC2 instances.
- **Reliable message passing** with SQS FIFO queues.
- **Batch processing** of images from S3.
- **Output of matched images** (with both face and text) and their extracted text to a single file.

---

## Setup Instructions

### 1. AWS Infrastructure

- **Create two EC2 instances** (Amazon Linux 2, t2.micro recommended).
- **Configure security groups** to allow SSH, HTTP, and HTTPS from your IP.
- **Install Java 1.8** on both instances:
  ```bash
  sudo yum install java-1.8.0-devel
  ```
- **Configure AWS credentials** on both EC2s:
  ```bash
  mkdir ~/.aws
  vi ~/.aws/credentials
  ```
  Add AWS access and secret keys.

- **Set up an S3 bucket** to store driver license images.
- **Create an SQS FIFO queue** (enable content-based deduplication).

### 2. Java Application

- **Clone this repository:**
  ```bash
  git clone https://github.com/sruthivellore/DriverLicenseDetailsExtraction.git
  ```
- **Build the Maven projects** for each application (`ec2a`, `ec2b`) and package as JARs.
- **Upload the JARs** to the respective EC2 instances (use WinSCP or `scp`).

### 3. Running the Applications

- On **EC2 A** (Face Detection):
  ```bash
  java -jar FaceDetectionApplication.jar
  ```
- On **EC2 B** (Text Extraction):
  ```bash
  java -jar TextExtractionApplication.jar
  ```

---

## Usage

1. Place driver license images in the configured S3 bucket.
2. Start both EC2 applications as described above.
3. The system will process images in parallel:
   - EC2 A detects faces and sends qualifying image indexes to SQS.
   - EC2 B reads from SQS, extracts text, and writes output.

---

## Output

- After processing, an `output.txt` file is generated in the home directory of EC2 B.
- This file contains:
  - Indexes of images containing both a face and text.
  - The extracted text for each image[1].

---

## Project Structure

```
DriverLicenseDetailsExtraction/                        
│   └── FaceDetectionApplication.jar # Face detection Java project          
│   └── TextExtractionApplication.jar # Text extraction Java project
```

---

## Dependencies

- Java 1.8+
- AWS SDK for Java
- AWS Rekognition
- AWS Textract
- AWS S3
- AWS SQS
- Maven (for building the projects)

---

- [Video Demo](https://youtu.be/ZER3IG9OBrA)

---

