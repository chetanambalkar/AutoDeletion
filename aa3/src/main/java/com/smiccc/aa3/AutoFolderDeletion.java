package com.smiccc.aa3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class AutoFolderDeletion {

    private static final Logger logger = LoggerFactory.getLogger(AutoFolderDeletion.class);

    private static String ROOT_PATH;
    private static String TEMP_PATH;
    private static int DELETION_FREQUENCY_DAYS;
    private static List<String> FOLDER_NAMES;

    public static void main(String[] args) {
        loadConfiguration();
        while (true) {
            try {
                autoMoveAndDeleteFiles();
            } catch (IOException e) {
                logger.error("Unexpected error during file moving and deletion", e);
            }
            // Sleep for the specified frequency
            try {
                Thread.sleep(TimeUnit.DAYS.toMillis(DELETION_FREQUENCY_DAYS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore the interrupted status
                logger.warn("Thread interrupted", e);
            }
        }
    }

    private static void loadConfiguration() {
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream("config.properties")) {
            properties.load(input);

            ROOT_PATH = properties.getProperty("ROOT_PATH");
            TEMP_PATH = properties.getProperty("TEMP_PATH");
            DELETION_FREQUENCY_DAYS = Integer.parseInt(properties.getProperty("DELETION_FREQUENCY_DAYS"));

            // Read folder names
            FOLDER_NAMES = Arrays.asList(properties.getProperty("FOLDER_NAMES").split(","));
        } catch (IOException | NumberFormatException e) {
            logger.error("Error reading configuration file or parsing integer", e);
            System.exit(1);
        }
    }

    private static void autoMoveAndDeleteFiles() throws IOException {
        // Iterate through folder names
        for (String folderName : FOLDER_NAMES) {
            Path folderPath = Paths.get(ROOT_PATH, folderName);

            // Iterate through files in the folder
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(folderPath)) {
                for (Path filePath : directoryStream) {
                    // Check last modified date
                    Date lastModifiedDate = new Date(Files.getLastModifiedTime(filePath).toMillis());
                    if (isOlderThanThreshold(lastModifiedDate)) {
                        // Delete existing file from the temporary folder
                        deleteFileFromTemp(filePath.toFile());

                        // Move the file to the temporary folder
                        moveFileToTemp(filePath, Paths.get(TEMP_PATH));
                    }
                }
            }
        }
    }

    private static void moveFileToTemp(Path source, Path destination) {
        // Move the file to the temporary folder
        try {
            Files.move(source, destination.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            logger.info("File moved to temp folder: " + source.toString());
        } catch (IOException e) {
            logger.error("Error moving file to temp folder", e);
        }
    }

    private static void deleteFileFromTemp(File file) {
        // Delete the file from the temporary folder
        Path tempFilePath = Paths.get(TEMP_PATH, file.getName());
        try {
            Files.deleteIfExists(tempFilePath);
            logger.info("File deleted from temp folder: " + tempFilePath.toString());
        } catch (IOException e) {
            logger.error("Error deleting file from temp folder", e);
        }
    }

    private static boolean isOlderThanThreshold(Date lastModifiedDate) {
        // Calculate the deletion threshold
        Date deletionThreshold = calculateDeletionThreshold();

        // Compare last modified date with the threshold
        return lastModifiedDate.before(deletionThreshold);
    }

    private static Date calculateDeletionThreshold() {
        // Get the current date
        Calendar calendar = Calendar.getInstance();

        // Subtract the deletion frequency days
        calendar.add(Calendar.DAY_OF_MONTH, -DELETION_FREQUENCY_DAYS);

        // Return the calculated date
        return calendar.getTime();
    }
}
