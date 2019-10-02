/**
 *
 */
package de.peldszus.consistency.tgg.gen.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedList;

import org.apache.log4j.Logger;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.gravity.eclipse.importer.DuplicateProjectNameException;
import org.gravity.eclipse.util.EclipseProjectUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.osgi.framework.Bundle;

import de.peldszus.consistency.tgg.gen.ui.ProjectCreationHandler;

/**
 * @author speldszus
 *
 */
public class GenTest {

	private static final Logger LOGGER = Logger.getLogger(GenTest.class);

	private final Collection<IProject> projects = new LinkedList<>();

	public static Collection<URL> collectMetaModels() throws URISyntaxException, MalformedURLException {
		final Bundle bundle = Platform.getBundle(Activator.PLUGIN_ID);
		final Enumeration<URL> entries = bundle.findEntries("data", "*.ecore", false);
		final Collection<URL> files = new LinkedList<>();
		while( entries.hasMoreElements()) {
			files.add(entries.nextElement());
		}
		return files;
	}

	@ParameterizedTest
	@MethodSource("collectMetaModels")
	public void testPlatformResource(URL model) throws IOException, DuplicateProjectNameException, CoreException {
		final NullProgressMonitor monitor = new NullProgressMonitor();
		final String tggProjectName = "TargetTGGProject";
		deleteProject(tggProjectName, monitor);
		final IProject metaModelProject = EclipseProjectUtil.createProject("TestProject", monitor);
		this.projects.add(metaModelProject);
		final IFile file = metaModelProject.getFile(model.getFile());
		final IContainer parent = file.getParent();
		if(!parent.exists()) {
			((IFolder) parent).create(IResource.DEPTH_ZERO, true, monitor);
		}
		try(InputStream in = model.openStream()){
			file.create(in, true, monitor);
		}
		final IStatus status = ProjectCreationHandler.createFromWorkspaceSelection(Arrays.asList(file), tggProjectName, monitor);
		assertEquals(IStatus.OK, status.getSeverity(), status.getMessage());
	}

	/**
	 * @param tggProjectName
	 * @param monitor
	 * @throws CoreException
	 */
	public void deleteProject(String tggProjectName, final NullProgressMonitor monitor) throws CoreException {
		IProject tggProject = EclipseProjectUtil.getProjectByName(tggProjectName);
		if(tggProject.exists()) {
			tggProject.delete(true, monitor);
		}
		tggProject = null;
	}

	@AfterEach
	public void clean() {
		this.projects.forEach(project -> {
			try {
				project.delete(true, new NullProgressMonitor());
			} catch (final CoreException e) {
				LOGGER.error(e);
			}
		});
	}
}
