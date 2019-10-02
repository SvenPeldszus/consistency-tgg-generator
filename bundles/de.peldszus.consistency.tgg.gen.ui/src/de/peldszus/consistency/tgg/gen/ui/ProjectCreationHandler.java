package de.peldszus.consistency.tgg.gen.ui;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JOptionPane;

import org.apache.log4j.Logger;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.gravity.eclipse.importer.DuplicateProjectNameException;

import de.peldszus.consistency.tgg.gen.TGGProjectCreator;

/**
 * A handler for triggering the creation of a TGG project
 *
 * @author speldszus
 *
 */
public class ProjectCreationHandler extends AbstractHandler {

	/*
	 * The logger of this class
	 */
	static final Logger LOGGER = Logger.getLogger(ProjectCreationHandler.class);

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		final List<?> selection = getSelection(event);

		final Job job = new Job("Generate eMoflon consistency TGG") {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				final String projectName = JOptionPane.showInputDialog("Please enter the name for the new TGG project.");
				return createFromWorkspaceSelection(selection, projectName, monitor);
			}

		};
		job.setUser(true);
		job.schedule();

		return null;
	}

	/**
	 * Loads all EPackages from the selected ecore files
	 *
	 * @param selection The selection from the workspace
	 * @return The EPackages contained in the selection
	 */
	private static Set<EPackage> loadEPackages(final List<?> selection) {
		final ResourceSet resourceSet = new ResourceSetImpl();
		return selection.parallelStream().filter(element -> (element instanceof IFile)).map(file -> (IFile) file)
				.filter(file -> file.getFileExtension().equals(Activator.FILEEXTENSION_ECORE)).flatMap(file -> {
					try {
						final String projectName = file.getProject().getName();
						final String projectRelavtive = file.getProjectRelativePath().toString();
						final Resource resource = resourceSet.createResource(URI.createPlatformResourceURI(projectName+ File.separator+projectRelavtive, true));
						resource.load(file.getContents(), Collections.emptyMap());
						return resource.getContents().parallelStream();
					} catch (IOException | CoreException e) {
						LOGGER.error("Couldn't load file: " + file.getName(), e);
						return Stream.empty();
					}
				}).filter(eObject -> {
					if (eObject instanceof EPackage) {
						return true;
					}
					LOGGER.error("Loaded object is no EPackage: " + eObject.eClass().getName());
					return false;
				}).map(eObject -> (EPackage) eObject).collect(Collectors.toSet());
	}

	/**
	 * Creates a TGG project from the selected ecore files in the workspace
	 *
	 * @param selection The selection from the workspace
	 * @param projectName The name of the created project
	 * @param monitor A progress monitor
	 * @return A status object describing the result
	 */
	public static IStatus createFromWorkspaceSelection(final List<?> selection, final String projectName, IProgressMonitor monitor) {
		final Set<EPackage> ePackages = loadEPackages(selection);
		if (ePackages.isEmpty()) {
			LOGGER.error("No EPackage selected!");
			return new Status(IStatus.ERROR, Activator.PLUGIN_ID, "No EPackage selected!");
		}
		try {
			new TGGProjectCreator().createTGGProject(ePackages, projectName, monitor);
		} catch (DuplicateProjectNameException | CoreException | IOException e) {
			LOGGER.error("Creation of a TGG project failed.", e);
			e.printStackTrace();
			return new Status(IStatus.ERROR, Activator.PLUGIN_ID, "Creation of a TGG project failed.", e);
		}
		return Status.OK_STATUS;
	}

	/**
	 * This operation gets the current selection in the workspace for an event
	 *
	 * @param event The current event
	 * @return The selected objects
	 * @throws ExecutionException Iff the selection cannot be determined
	 */
	public static List<Object> getSelection(ExecutionEvent event) throws ExecutionException {
		final IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		final ISelectionService service = window.getSelectionService();
		final IStructuredSelection structured = (IStructuredSelection) service.getSelection();
		if (structured == null) {
			throw new ExecutionException("No projects have been selected for discovery!");
		}
		return Arrays.asList(structured.toArray());
	}
}
