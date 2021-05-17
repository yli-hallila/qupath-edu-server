package fi.ylihallila.server.controllers;

import fi.ylihallila.server.authentication.Authenticator;
import fi.ylihallila.server.models.Slide;
import fi.ylihallila.server.models.User;
import fi.ylihallila.server.util.Constants;
import fi.ylihallila.server.util.OpenSlideCache;
import fi.ylihallila.server.util.Util;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UploadedFile;
import org.hibernate.Session;
import org.openslide.OpenSlide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class SlideController extends Controller {

	private Logger logger = LoggerFactory.getLogger(SlideController.class);

	public void getAllSlides(Context ctx) {
		Session session = ctx.use(Session.class);

		List<Slide> slides = session.createQuery("from Slide", Slide.class).list();

		List<HashMap<String, Object>> slidesWithProperties = slides.stream().map(slide -> {
			HashMap<String, Object> data = new HashMap<>();
			data.put("name", slide.getName());
			data.put("id", slide.getId());
			data.put("owner", slide.getOwner());

			File propertiesFile = new File(String.format(Constants.SLIDE_PROPERTIES_FILE, slide.getId()));

			if (propertiesFile.exists()) {
				try {
					data.put("parameters", Util.getMapper().readValue(propertiesFile, Map.class)); // TODO: Rename Properties
				} catch (IOException e) {
					logger.error("Error while trying to get properties for slide {}", slide.getId(), e);
				}
			}

			return data;
		}).collect(Collectors.toList());

		ctx.status(200).json(slidesWithProperties);
	}

	public void upload(Context ctx) throws IOException {
		String fileName = ctx.queryParam("filename");
		long totalSize  = ctx.queryParam("fileSize", Long.class).get();
		long buffer     = ctx.queryParam("chunkSize", Integer.class).get();
		long index      = ctx.queryParam("chunk", Integer.class).get();

		UploadedFile file = ctx.uploadedFile("file");

		if (file != null) {
			logger.trace("Uploading slide chunk: {} [Size: {}, Buffer: {}, Index: {}]", fileName, totalSize, buffer, index);

			byte[] data = file.getContent().readAllBytes();

			RandomAccessFile writer = new RandomAccessFile(String.format(Constants.TEMP_FILE, fileName), "rw");
			writer.seek(index * buffer);
			writer.write(data, 0, data.length);
			writer.close();

			// The file is fully uploaded we can start processing it.
			if (Files.size(Path.of(String.format(Constants.TEMP_FILE, fileName))) == totalSize) {
				processUploadedSlide(ctx, fileName);
			}

			ctx.status(200);
		} else {
			ctx.status(400);
		}
	}

	public void updateSlide(Context ctx) {
		String id = ctx.pathParam("slide-id", String.class).get();
		User user = Authenticator.getUser(ctx);
		Session session = ctx.use(Session.class);

		Slide slide = session.find(Slide.class, id);
		if (slide == null) {
			throw new NotFoundResponse();
		}

		if (!slide.hasPermission(user)) {
			throw new ForbiddenResponse();
		}

		slide.setName(ctx.formParam("slide-name", slide.getName()));
		session.update(slide);

		ctx.status(200);

		logger.info("Slide {} edited by {}", id, Authenticator.getUsername(ctx).orElse("Unknown"));
	}

	public void deleteSlide(Context ctx) throws IOException {
		String id = ctx.pathParam("slide-id", String.class).get();
		User user = Authenticator.getUser(ctx);
		Session session = ctx.use(Session.class);

		Slide slide = session.find(Slide.class, id);
		if (slide == null) {
			throw new NotFoundResponse();
		}

		if (!slide.hasPermission(user)) {
			throw new ForbiddenResponse();
		}

		session.delete(slide);

		Path propertiesPath = Path.of(String.format(Constants.SLIDE_PROPERTIES_FILE, id));
		backup(propertiesPath);
		Files.delete(propertiesPath);

		ctx.status(200);

		logger.info("Slide {} deleted by {}", id, Authenticator.getUsername(ctx).orElse("Unknown"));
	}

	public void getSlideProperties(Context ctx) throws IOException {
		if (ctx.queryParam("openslide") != null) {
			getSlidePropertiesFromOpenslide(ctx);
		} else {
			getSlidePropertiesFromFile(ctx);
		}
	}

	private void getSlidePropertiesFromFile(Context ctx) throws IOException {
		String id = ctx.pathParam("slide-id", String.class).get();

		File propertiesFile = new File(String.format(Constants.SLIDE_PROPERTIES_FILE, id));

		if (propertiesFile.exists()) {
			ctx.status(200).json(Util.getMapper().readValue(propertiesFile, Map.class));
		} else {
			throw new NotFoundResponse();
		}
	}

	private void getSlidePropertiesFromOpenslide(Context ctx) throws IOException {
		Map<String, String> properties = OpenSlideCache.get(ctx.pathParam("slide-name")).get().getProperties();

		ctx.status(200).json(properties); // TODO: Test
	}

	public void renderTile(Context ctx) throws Exception {
		String slide   = ctx.pathParam("slide-id");
		int tileX      = ctx.pathParam("tileX", Integer.class).get();
		int tileY      = ctx.pathParam("tileY", Integer.class).get();
		int level      = ctx.pathParam("level", Integer.class).get();
		int tileWidth  = ctx.pathParam("tileWidth", Integer.class).get();
		int tileHeight = ctx.pathParam("tileHeight", Integer.class).get();

		String fileName = String.format(Constants.TILE_FILE_FORMAT, slide, tileX, tileY, level, tileWidth, tileHeight);

		if (Files.exists(Path.of(fileName), LinkOption.NOFOLLOW_LINKS)) {
			logger.trace("Retrieving from disk [{}, {},{} / {} / {},{}]", fileName, tileX, tileY, level, tileWidth, tileHeight);

			FileInputStream fis = new FileInputStream(Path.of(fileName).toString());
			InputStream is = new ByteArrayInputStream(fis.readAllBytes());

			ctx.status(200).contentType("image/jpg").result(is);

			is.close();
			fis.close();
		} else {
			logger.info("Couldn't find tile [{}, {},{} / {} / {},{}]", fileName, tileX, tileY, level, tileWidth, tileHeight);
			throw new NotFoundResponse();
		}
	}

	private void processUploadedSlide(Context ctx, String slideName) throws IOException {
		Optional<OpenSlide> openSlide = OpenSlideCache.get(String.format(Constants.TEMP_FILE, slideName));

		if (openSlide.isEmpty()) {
			logger.error("Error when processing uploaded file: Couldn't create OpenSlide instance."
				+ "\n" + "Possible solutions: file was corrupted during upload or the file isn't supported by OpenSlide");
			return;
		} else {
			logger.info("Processing slide {}, uploaded by {}", slideName, Authenticator.getUsername(ctx).orElse("Unknown"));
		}

		String id = UUID.randomUUID().toString();
		User user = Authenticator.getUser(ctx);

		// Add slide to slides.json

		Session session = ctx.use(Session.class);

		Slide slide = new Slide();
		slide.setName(slideName);
		slide.setId(id);
		slide.setOwner(user.getOrganization());
		session.save(slide);

		// Move slide to slides pending tiling. See Tiler for further processing.

		Files.move(
			Path.of(String.format(Constants.TEMP_FILE, slideName)),
			Path.of(String.format(Constants.PENDING_SLIDES, id))
		);
	}
}
