package lu.snt.serval.astdiff;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.astDiff.actions.ASTDiff;
import org.refactoringminer.astDiff.actions.ProjectASTDiff;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;

import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.kohsuke.github.*;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) {
        GitHub github= connectGithub();

        JSONArray jsonArray = new JSONArray();
        JSONArray outputJsonArray = new JSONArray();
        JSONArray errorJsonArray = new JSONArray();
        try {
            // Read JSON file as string
            String jsonStr = new String(Files.readAllBytes(Paths.get("src/test/resources/data.json")));
            // Parse JSON string to JSON array
            jsonArray = new JSONArray(jsonStr);
        } catch (IOException e) {
            System.err.println("Error reading JSON file: " + e.getMessage());
            JSONObject errorEntry = new JSONObject();
            errorEntry.put("status", "error");
            errorEntry.put("message", "Error reading JSON file: " + e.getMessage());
            errorJsonArray.put(errorEntry);
        }

        // Iterate over each entry in the JSON array
        for (int i =0; i < 1000; i++) {
            JSONObject logEntry = new JSONObject();
            try {
                // Get the current entry
                JSONObject entry = jsonArray.getJSONObject(i);

                // Extract values of "repository" and "URL"
                String repositoryUrl = entry.getString("repository");
                String url = entry.getString("url");

                // Print the extracted values
                System.out.println("Entry " + (i + 1) + ":");
                System.out.println("Repository: " + repositoryUrl);
                System.out.println("URL: " + url);
                System.out.println();

                // Log the extracted values
                logEntry.put("entryIndex", i + 1);
                logEntry.put("repository", repositoryUrl);
                logEntry.put("url", url);

                // Analyze commit and get changed Java files
                GHRepository repository = getGHRepository(repositoryUrl, github);
                GHCommit commit = getGHCommit(github, repository, url);
                if (commit == null) {
                    System.err.println("Commit not found for URL: " + url);
                    logEntry.put("status", "error");
                    logEntry.put("message", "Commit not found");
                    errorJsonArray.put(logEntry);
                    continue;
                }
                List<String> javaFiles = getJavaFiles(commit);
                List<String> mainFiles = filterMainFiles(javaFiles);
                List<String> testFiles = filterTestFiles(javaFiles);

                if (!testFiles.isEmpty()) {
                    for (String filePath : mainFiles) {
                        JSONObject resultJson = new JSONObject();
                        resultJson.put("repository", repositoryUrl);
                        resultJson.put("commit", url);
                        resultJson.put("fileType", "main");
                        resultJson.put("filePath", filePath);

                        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
                        resultJson.put("fileName", fileName);
                        List<RefactoringDetails> refactorings = getRefactoring(repository, commit, fileName, filePath);

                        JSONArray refactoringsArray = new JSONArray();
                        refactorings.forEach(refactoring -> refactoringsArray.put(refactoring.toJson()));
                        resultJson.put("refactorings", refactoringsArray);

                        outputJsonArray.put(resultJson);
                    }

                    for (String filePath : testFiles) {
                        JSONObject resultJson = new JSONObject();
                        resultJson.put("repository", repositoryUrl);
                        resultJson.put("commit", url);
                        resultJson.put("fileType", "test");
                        resultJson.put("filePath", filePath);

                        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
                        resultJson.put("fileName", fileName);
                        List<RefactoringDetails> refactorings = getRefactoring(repository, commit, fileName, filePath);

                        JSONArray refactoringsArray = new JSONArray();
                        refactorings.forEach(refactoring -> refactoringsArray.put(refactoring.toJson()));
                        resultJson.put("refactorings", refactoringsArray);

                        outputJsonArray.put(resultJson);
                    }
                }

                // Mark the log entry as successful
                logEntry.put("status", "success");
                errorJsonArray.put(logEntry);

            } catch (Exception e) {
                System.err.println("Error processing entry " + (i + 1) + ": " + e.getMessage());
                e.printStackTrace();
                logEntry.put("status", "error");
                logEntry.put("message", e.getMessage());
                errorJsonArray.put(logEntry);
            }
        }

        // Save the output JSON array to a file
        try {
            Files.write(Paths.get("src/test/resources/output_refactorings.json"), outputJsonArray.toString(2).getBytes());
            Files.write(Paths.get("src/test/resources/error_log.json"), errorJsonArray.toString(2).getBytes());
        } catch (IOException e) {
            System.err.println("Error writing JSON file: " + e.getMessage());
        }
    }

    public static GitHub connectGithub() {
        GitHub github = null;
        try {
            github = new GitHubBuilder().withOAuthToken("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", "mahaayub").build();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return github;
    }

    public static List<String> filterMainFiles(List<String> javafiles){
        List<String> mainFiles = new ArrayList<>();
        for (String filename : javafiles) {
            if (!filename.toLowerCase().contains("test")) {
                mainFiles.add(filename);
            }
        }
        System.out.println("Main Files:");
        for (String file : mainFiles) {
            System.out.println(file);
        }
        return mainFiles;
    }
    public static List<String> filterTestFiles(List<String> javafiles){
        List<String> testFiles = new ArrayList<>();
        for (String filename : javafiles) {
            if (filename.toLowerCase().contains("test")) {
                testFiles.add(filename);
            }
        }
        System.out.println("Test Files:");
        for (String file : testFiles) {
            System.out.println(file);
        }
        return testFiles;
    }
    public static GHRepository getGHRepository(String jsonUrl,GitHub gitHub) throws IOException {

        // Parse repository owner and name from JSON URL
        String[] parts = jsonUrl.split("/");
        String owner = parts[parts.length - 2];
        String repoName = parts[parts.length - 1].replace(".git", ""); // Remove '.git' if present

        // Get repository
        GHRepository repository = gitHub.getRepository(owner + "/" + repoName);
        return repository;
    }
    public static GHCommit getGHCommit(GitHub gitHub, GHRepository repository , String commitUrl) throws IOException {
        GHCommit commit = null;
        try {
            String[] commitParts = commitUrl.split("/");
            String commitSHA = commitParts[commitParts.length - 1];

            // Get commit
            commit = repository.getCommit(commitSHA);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return commit;
    }

    public static List<String> getJavaFiles(GHCommit commit) {
        List<String> changedJavaFiles = new ArrayList<>();
        try {
            // Get changed files in the commit
            List<GHCommit.File> files = commit.getFiles();

            // Iterate over the files to find .java files
            for (GHCommit.File file : files) {
                String filename = file.getFileName();
                if (filename.endsWith(".java")) {
                    changedJavaFiles.add(filename);
                }
            }
            System.out.println("Changed Java Files:");
            for (String file : changedJavaFiles) {
                System.out.println(file);
            }

        } catch (IOException e) {
            System.err.println("Error fetching commit details: " + e.getMessage());
        }
        return changedJavaFiles;
    }
        public static List<RefactoringDetails> getRefactoring(GHRepository repository, GHCommit commit, String fileName, String filePath) {
            final ObjectMapper objectMapper = new ObjectMapper();

            try {
                // Get file content at current commit
                GHContent fileContentAfter = repository.getFileContent(filePath, commit.getSHA1());
                String contentAfter = new String(fileContentAfter.read().readAllBytes());

                // Get file content at previous commit (parent of the current commit)
                GHCommit parentCommit = commit.getParents().get(0);
                GHContent fileContentBefore = repository.getFileContent(filePath, parentCommit.getSHA1());
                String contentBefore = new String(fileContentBefore.read().readAllBytes());

                // Save content to local files
                String fileNameBefore = "src/test/resources/testbeforeclass/" + fileName;
                String fileNameAfter = "src/test/resources/testafterclass/" + fileName;
                Files.write(Paths.get(fileNameBefore), contentBefore.getBytes());
                Files.write(Paths.get(fileNameAfter), contentAfter.getBytes());

                // Apply refactoring
                GitHistoryRefactoringMiner miner = new GitHistoryRefactoringMinerImpl();
                File dir1 = new File(fileNameBefore);
                File dir2 = new File(fileNameAfter);
                ProjectASTDiff diff = miner.diffAtDirectories(dir1, dir2);
                Set<ASTDiff> diffs = diff.getDiffSet();

                // Get refactoring data as JSON string and store it
                List<Refactoring> refactorings = diff.getRefactorings();
               // System.out.println("Json Refactorings:"+ refactorings.getLast().toJSON());

                if (refactorings.isEmpty()) {
                    System.out.println("No refactorings found in : " + fileName);
                }

                return refactorings.stream().map(refactoring -> {
                    try {
                        RefactoringDetails details = new RefactoringDetails();
                        JsonNode refactoringJson = objectMapper.readTree(refactoring.toJSON());
                        String type = refactoringJson.get("type").asText();
                        String description = refactoringJson.get("description").asText();

                        int rightSideLineNumber = -1;

                        // Extract the right side location line number
                        JsonNode rightSideLocations = refactoringJson.get("rightSideLocations");
                        if (rightSideLocations != null && rightSideLocations.isArray()) {
                            for (JsonNode location : rightSideLocations) {
                                rightSideLineNumber = location.get("startLine").asInt();
                            }
                        }

                        System.out.println(type + " " + description);

                        details.setType(type);
                        details.setDescription(description);
                        details.setRightSideLineNumber(rightSideLineNumber);

                        return details;
                    } catch (IOException e) {
                        e.printStackTrace();
                        return null;
                    }
                }).collect(Collectors.toList());
            } catch (IOException e) {
                System.err.println("Error fetching commit details: " + e.getMessage());
                return List.of();
            }
        }




}
