package fi.ylihallila.server.controllers;

import fi.ylihallila.server.gson.Workspace;
import io.javalin.http.Context;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

public class WorkspaceController extends BasicController {

	public void getWorkspaces(Context ctx) throws IOException {
		// Middleware > File Exists
		ctx.result(Files.readString(getWorkspaceFile()));
		ctx.status(200).contentType("application/json");
	}

	public void createWorkspace(Context ctx) throws IOException {
		List<Workspace> workspaces = getWorkspaces();

		Workspace workspace = new Workspace();
		workspace.setName(ctx.formParam("workspace-name", String.class).get());
		workspace.setProjects(Collections.emptyList());
		workspaces.add(workspace);

		saveWorkspace(workspaces);
		backup(getWorkspaceFile());
	}

	public void deleteWorkspace(Context ctx) throws IOException {
		List<Workspace> workspaces = getWorkspaces();
		var success = workspaces.removeIf(project ->
			project.getName().equalsIgnoreCase(ctx.pathParam("workspace-name"))
		);

		if (success) {
			saveWorkspace(workspaces);
			backup(getWorkspaceFile());
			ctx.status(200);
		} else {
			ctx.status(404);
		}
	}
}