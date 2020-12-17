package fi.ylihallila.server;

import fi.ylihallila.server.authentication.Authenticator;
import fi.ylihallila.server.controllers.*;
import fi.ylihallila.server.util.Constants;
import fi.ylihallila.server.util.Database;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.apache.http.HttpVersion;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;

import java.nio.file.Path;

import static io.javalin.apibuilder.ApiBuilder.*;
import static io.javalin.core.security.SecurityUtil.roles;
import static fi.ylihallila.server.commons.Roles.*;
import static fi.ylihallila.server.util.Config.Config;

public class Application {

    private Logger logger = LoggerFactory.getLogger(Application.class);
    private Javalin app = Javalin.create(config -> {
        config.accessManager(Authenticator::accessManager);
        config.showJavalinBanner = false;
        config.maxRequestSize = Long.MAX_VALUE;
        config.addStaticFiles("/logos", Path.of("organizations").toAbsolutePath().toString(), Location.EXTERNAL);
        config.addStaticFiles("/tiles", Path.of("tiles").toAbsolutePath().toString(), Location.EXTERNAL);

        config.server(() -> {
            Server server = new Server();

            if (Constants.SECURE_SERVER) {
//                config.enforceSsl = true;

                HttpConfiguration httpConfig = new HttpConfiguration();
                httpConfig.setSecureScheme("https");
                httpConfig.setSecurePort(Config.getInt("server.port.secure"));

                SecureRequestCustomizer src = new SecureRequestCustomizer();
                httpConfig.addCustomizer(src);

                HttpConnectionFactory httpConnectionFactory = new HttpConnectionFactory(httpConfig);
                SslConnectionFactory sslConnectionFactory = new SslConnectionFactory(getSslContextFactory(), HttpVersion.HTTP_1_1.toString());

                ServerConnector sslConnector = new ServerConnector(server,
                    new OptionalSslConnectionFactory(sslConnectionFactory, HttpVersion.HTTP_1_1.toString()),
                    sslConnectionFactory,
                    httpConnectionFactory);
                sslConnector.setPort(Config.getInt("server.port.secure"));

                server.addConnector(sslConnector);
            } else {
                ServerConnector connector = new ServerConnector(server);
                connector.setPort(Config.getInt("server.port.insecure"));
                server.addConnector(connector);
            }

            return server;
        });
    }).start();

    private OrganizationController OrganizationController;
    private WorkspaceController WorkspaceController;
    private ProjectController ProjectController;
    private SubjectController SubjectController;
    private BackupController BackupController;
    private SlideController SlideController;
    private UserController UserController;

    public Application() {
        createControllers();

        app.routes(() -> path("/api/v0/", () -> {
            before(ctx -> { // TODO: Annotate methods with @Database; only these have Session available to save resources?
                logger.debug("Creating Database Session for Request");

                Session session = Database.getSession();
                session.beginTransaction();

                ctx.register(Session.class, session);
            });

            after(ctx -> {
                logger.debug("Destroying Database Session for Request");

                Session session = ctx.use(Session.class);

                if (session != null && session.getTransaction() != null) {
                    session.getTransaction().commit();
                    session.close();
                }
            });

            /* Authentication */
            path("users", () -> {
                get(UserController::getAllUsers,                roles(MANAGE_USERS));
                get("login", UserController::login,             roles(ANYONE));
                get("verify", UserController::verify,           roles(ANYONE));
                get("write/:id", UserController::hasPermission, roles(ANYONE));

                path(":user-id", () -> {
                    get(UserController::getUser,    roles(MANAGE_USERS));
                    put(UserController::updateUser, roles(MANAGE_USERS));
                });
            });

            /* Upload */
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
                get(ProjectController::getAllProjects,                          roles(ADMIN));
                post(ProjectController::createProject,                          roles(MANAGE_PROJECTS));
                post("personal", ProjectController::createPersonalProject, roles(MANAGE_PERSONAL_PROJECTS));

                path(":project-id", () -> {
                    get(ProjectController::downloadProject,  roles(ANYONE));
                    put(ProjectController::updateProject,    roles(MANAGE_PERSONAL_PROJECTS, MANAGE_PROJECTS));
                    delete(ProjectController::deleteProject, roles(MANAGE_PERSONAL_PROJECTS, MANAGE_PROJECTS));
                    post(ProjectController::uploadProject,   roles(MANAGE_PERSONAL_PROJECTS, MANAGE_PROJECTS));
                });
            });

            /* Subjects */
            path("subjects", () -> {
                post(SubjectController::createSubject, roles(MANAGE_PROJECTS, MANAGE_PERSONAL_PROJECTS));

                path(":subject-id", () -> {
                   put(SubjectController::updateSubject, roles(MANAGE_PERSONAL_PROJECTS, MANAGE_PROJECTS));
                   delete(SubjectController::deleteSubject, roles(MANAGE_PERSONAL_PROJECTS, MANAGE_PROJECTS));
                });
            });

            /* Backups */
            path("backups", () -> {
                get(BackupController::getAllBackups, roles(MANAGE_PROJECTS));

                get("restore/:file/:timestamp", BackupController::restore, roles(MANAGE_PROJECTS));
            });

            /* Organizations */
            path("organizations", () -> {
                get(OrganizationController::getAllOrganizations, roles(ANYONE));

                // TODO: Write APIs to manage Organizations
            });
        }));
    }

    private void createControllers() {
        this.OrganizationController = new OrganizationController();
        this.WorkspaceController = new WorkspaceController();
        this.ProjectController = new ProjectController();
        this.SubjectController = new SubjectController();
        this.BackupController = new BackupController();
        this.SlideController = new SlideController();
        this.UserController = new UserController();
    }

    private SslContextFactory getSslContextFactory() {
        try {
            SslContextFactory sslContextFactory = new SslContextFactory();

            URL path = Application.class.getProtectionDomain().getCodeSource().getLocation();

            sslContextFactory.setKeyStorePath(path.toURI().resolve(Config.getString("ssl.keystore.path")).toASCIIString());
            sslContextFactory.setKeyStorePassword(Config.getString("ssl.keystore.password"));
            return sslContextFactory;
        } catch (URISyntaxException e) {
            logger.error("Couldn't start HTTPS server, no valid keystore.");
            return null;
        }
    }
}
