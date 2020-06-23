package fi.ylihallila.server;

import fi.ylihallila.server.authentication.Authenticator;
import fi.ylihallila.server.controllers.*;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.plugin.rendering.vue.JavalinVue;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.net.URL;

import io.javalin.plugin.rendering.vue.VueComponent;

import static io.javalin.apibuilder.ApiBuilder.*;
import static io.javalin.core.security.SecurityUtil.roles;
import static fi.ylihallila.server.authentication.Roles.*;

public class SecureServer {

    private Logger logger = LoggerFactory.getLogger(SecureServer.class);
    private Javalin app = Javalin.create(config -> {
        config.accessManager(Authenticator::accessManager);
        config.showJavalinBanner = false;
        config.enableWebjars();
        config.requestCacheSize = Long.MAX_VALUE;
        config.addStaticFiles("/static", Location.CLASSPATH);

        JavalinVue.rootDirectory("/vue", Location.CLASSPATH);

        config.server(() -> {
            Server server = new Server();

            if (Config.SECURE_SERVER) {
                config.enforceSsl = true;

                ServerConnector sslConnector = new ServerConnector(server, getSslContextFactory());
                sslConnector.setPort(7000);
                server.addConnector(sslConnector);
            } else {
                ServerConnector connector = new ServerConnector(server);
                connector.setPort(7777);
                server.addConnector(connector);
            }

            return server;
        });
    }).start();

    private WorkspaceController WorkspaceController = new WorkspaceController();
    private ProjectController ProjectController = new ProjectController();
    private SlideController SlideController = new SlideController();
    private UserController UserController = new UserController();

    public SecureServer() {
        app.get("/", new VueComponent("index"),          roles(TEACHER, ADMIN));
        app.get("/upload", new VueComponent("uploader"), roles(ADMIN));

        /* API */
        app.get("/api/v1/slides", SlideController::GetAllSlidesV2, roles(ANYONE));

        app.routes(() -> path("/api/v0/", () -> {
            /* Authentication */
            path("users", () -> {
                get(UserController::getAllUsers,                     roles(ANYONE));
                get("login", UserController::login,             roles(STUDENT, TEACHER, ADMIN));
                get("verify", UserController::verify,           roles(ANYONE));
                get("write/:id", UserController::hasPermission, roles(ANYONE));

                path(":user-id", () -> {
                    get(UserController::getUser,       roles(ANYONE));
                    patch(UserController::updateUser,  roles(ADMIN));
                });
            });

            /* Upload */
            get("upload",  SlideController::upload,  roles(MANAGE_SLIDES));
            post("upload", SlideController::upload, roles(MANAGE_SLIDES));

            /* Slides */
            path("slides", () -> {
                get(SlideController::getAllSlides, roles(ANYONE));

                path(":slide-id", () -> {
                    get(SlideController::getSlideProperties, roles(ANYONE));
                    put(SlideController::updateSlide,        roles(MANAGE_SLIDES));
                    delete(SlideController::deleteSlide,     roles(MANAGE_SLIDES));
                    get("tile/:tileX/:tileY/:level/:tileWidth/:tileHeight", SlideController::renderTile, roles(ANYONE));
                });
            });

            /* Workspaces */
            path("workspaces", () -> {
                get(WorkspaceController::getAllWorkspaces, roles(ANYONE));
                post(WorkspaceController::createWorkspace, roles(MANAGE_PROJECTS));

                path(":workspace-id", () -> {
                    get(WorkspaceController::getWorkspace,       roles(ANYONE));
                    put(WorkspaceController::updateWorkspace,    roles(MANAGE_PROJECTS));
                    delete(WorkspaceController::deleteWorkspace, roles(MANAGE_PROJECTS));
                });
            });

            /* Projects */
            path("projects", () -> {
                get(ProjectController::getAllProjects,                          roles(ANYONE));
                post(ProjectController::createProject,                          roles(MANAGE_PROJECTS));
                post("personal", ProjectController::createPersonalProject, roles(MANAGE_PERSONAL_PROJECTS));

                path(":project-id", () -> {
                    get(ProjectController::downloadProject,  roles(ANYONE));
                    put(ProjectController::updateProject,    roles(MANAGE_PERSONAL_PROJECTS, MANAGE_PROJECTS));
                    delete(ProjectController::deleteProject, roles(MANAGE_PERSONAL_PROJECTS, MANAGE_PROJECTS));
                    post(ProjectController::uploadProject,   roles(MANAGE_PERSONAL_PROJECTS, MANAGE_PROJECTS));
                });
            });
        }));
    }

    private SslContextFactory getSslContextFactory() {
        try {
            SslContextFactory sslContextFactory = new SslContextFactory();

            URL path = SecureServer.class.getProtectionDomain().getCodeSource().getLocation();

            sslContextFactory.setKeyStorePath(path.toURI().resolve("keystore.jks").toASCIIString());
            sslContextFactory.setKeyStorePassword("qwerty");
            return sslContextFactory;
        } catch (URISyntaxException e) {
            logger.error("Couldn't start HTTPS server, no valid keystore.");
            return null;
        }
    }

    public WorkspaceController getWorkspaceController() {
        return WorkspaceController;
    }

    public ProjectController getProjectController() {
        return ProjectController;
    }

    public SlideController getSlideController() {
        return SlideController;
    }

    public UserController getUserController() {
        return UserController;
    }
}
