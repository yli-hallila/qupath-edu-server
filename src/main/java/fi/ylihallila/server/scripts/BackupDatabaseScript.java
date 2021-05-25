package fi.ylihallila.server.scripts;

import fi.ylihallila.server.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class BackupDatabaseScript extends Script {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override String getName() {
        return "Backup database daily";
    }

    @Override long getInterval() {
        return TimeUnit.DAYS.toSeconds(1);
    }

    @Override public void run() {
        String databaseFileName = "database.mv.db";

        Path databasePath = Path.of(databaseFileName);
        Path backupFilePath = Path.of(String.format(Constants.BACKUP_FILE_FORMAT, databaseFileName, System.currentTimeMillis()));

        try {
            Files.isHidden(databasePath);
//            Files.copy(databasePath, backupFilePath);
            logger.info("Database backed up.");
        } catch (IOException e) {
            logger.error("Error while trying to backup database", e);
        }
    }
}
