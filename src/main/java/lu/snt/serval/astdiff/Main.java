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

        try {
            // Read JSON file as string
            String jsonStr = new String(Files.readAllBytes(Paths.get("src/test/resources/data.json")));
            // Parse JSON string to JSON array
            JSONArray jsonArray = new JSONArray(jsonStr);
            JSONArray outputJsonArray = new JSONArray();


            // Iterate over each entry in the JSON array
            for (int i = 0; i < 100; i++) {
                // Get the current entry
                JSONObject entry = jsonArray.getJSONObject(i);

                // Extract values of "repository" and "URL"
                String repositoryurl = entry.getString("repository");
                String url = entry.getString("url");

                // Print the extracted values
                System.out.println("Entry " + (i + 1) + ":");
                System.out.println("Repository: " + repositoryurl);
                System.out.println("URL: " + url);
                System.out.println();

                // Analyze commit and get changed java files
                GHRepository repository = getGHRepository(repositoryurl,github);
                GHCommit commit= getGHCommit(github,repository,url);
                if (commit == null) {
                    System.err.println("Commit not found for URL: " + url);
                    continue;
                }
                List<String> javaFiles = getJavaFiles(commit);
                List<String> mainFiles = filterMainFiles (javaFiles);
                List<String> testFiles = filterTestFiles (javaFiles);
                if (!testFiles.isEmpty()) {
                    List<RefactoringDetails> allRefactorings = new ArrayList<>();

                    for (String filePath : mainFiles) {
                        JSONObject resultJson = new JSONObject();
                        resultJson.put("repository", repositoryurl);
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
                        resultJson.put("repository", repositoryurl);
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
            }
            // Save the output JSON array to a file
            Files.write(Paths.get("src/test/resources/output_refactorings.json"), outputJsonArray.toString(2).getBytes());

        } catch (IOException e) {
            System.err.println("Error reading JSON file: " + e.getMessage());
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

                if (refactorings.isEmpty()) {
                    System.out.println("No refactorings found in : " + fileName);
                }

                return refactorings.stream().map(refactoring -> {
                    try {
                        RefactoringDetails details = new RefactoringDetails();
                        JsonNode refactoringJson = objectMapper.readTree(refactoring.toJSON());
                        String type = refactoringJson.get("type").asText();
                        String description = refactoringJson.get("description").asText();
                        System.out.println(type + " " + description);
                        details.setType(type);
                        details.setDescription(description);
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
