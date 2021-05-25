package fi.ylihallila.server.scripts;

import fi.ylihallila.server.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class RemoveTemporaryFiles extends Script {

    private final long TWO_WEEKS = TimeUnit.DAYS.toMillis(14);

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override String getName() {
        return "Remove backups older than a year daily";
    }

    @Override long getInterval() {
        return TimeUnit.DAYS.toSeconds(1);
    }

    @Override public void run() {
        long NOW = System.currentTimeMillis();

        Path tempDirectory = Path.of(Constants.TEMP_DIRECTORY);

        try {
            for (File file : Files.list(tempDirectory).map(Path::toFile).collect(Collectors.toList())) {
                if (file.isDirectory()) {
                    continue;
                }

                if (NOW > file.lastModified() + TWO_WEEKS) {
//                    Files.delete(file.toPath());
                    logger.info("Deleted temporary file: {}", file.getName());
                }
            }
        } catch (IOException e) {
            logger.error("Error while deleting old temporary files", e);
        }
    }
}
