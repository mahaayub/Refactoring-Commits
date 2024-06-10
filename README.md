# Refactoring Miner GitHub Analyzer

This repository analyzes GitHub commits to extract and categorize changed Java files, applying the Refactoring Miner tool to identify refactoring operations. The results are saved in a JSON file.

## Overview

The application performs the following steps:

1. Takes a commit URL and a repository URL from a JSON input file.
2. Extracts the list of changed Java files in the specified commit.
3. Filters the Java files into main files and test files.
4. Saves the content of the Java files before and after the commit in the `test/resources` directory.
5. Applies the Refactoring Miner tool to extract refactoring details.
6. Stores the extracted refactoring details in a JSON file named `output_refactorings.json` in the `test/resources` directory.

## Prerequisites

- Java 8 or higher
- Maven
- A GitHub personal access token with repository access permissions

## Setup

1. **Clone the repository:**

   ```sh
   git clone https://github.com/your-username/refactoring-miner-github-analyzer.git
   cd refactoring-miner-github-analyzer
2. **Configure GitHub Access:**
     Open the Main.java file located in src/main/java/lu/snt/serval/astdiff/Main.java and update the connectGithub method with your GitHub username and personal access token
3. **Build the project: ** 
     Use Maven to build the project:
    ```sh
     mvn clean install

## Run the Main Function

Execute the main method in the Main class:
     
          mvn exec:java -Dexec.mainClass="lu.snt.serval.astdiff.Main"
          
Alternatively, you can run the main method directly from your IDE.

## Output
The application will generate a JSON file named `output_refactorings.json` in the `src/test/resources` directory containing the refactoring details.

The Java files before and after the commit will be saved in the `src/test/resources/testbeforeclass` and `src/test/resources/testafterclass` directories, respectively.

