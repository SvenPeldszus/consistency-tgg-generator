/**
 *
 */
package de.peldszus.consistency.tgg.gen.create;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.xtext.ui.XtextProjectHelper;
import org.emoflon.ibex.tgg.ide.admin.IbexTGGNature;
import org.gravity.eclipse.importer.DuplicateProjectNameException;
import org.gravity.eclipse.io.ExtensionFileVisitor;
import org.gravity.eclipse.util.EclipseProjectUtil;
import org.moflon.tgg.mosl.defaults.AttrCondDefLibraryProvider;
import org.moflon.tgg.mosl.tgg.AttrCondDef;
import org.moflon.tgg.mosl.tgg.TripleGraphGrammarFile;

import de.peldszus.consistency.tgg.gen.Activator;

/**
 * @author speldszus
 *
 */
public class EclipseProjectCreator {

	private IProject project;
	private final ResourceSet resourceSet;
	private Set<AttrCondDef> attrConds;

	public EclipseProjectCreator(ResourceSet resourceSet) {
		this.resourceSet = resourceSet;
	}

	public IProject createTGGProject(String name, IProgressMonitor monitor)
			throws DuplicateProjectNameException, CoreException, IOException {
		this.project = EclipseProjectUtil.createProject(name, monitor);
		EclipseProjectUtil.addNature(this.project, IbexTGGNature.IBEX_TGG_NATURE_ID, monitor);
		EclipseProjectUtil.addNature(this.project, IbexTGGNature.PLUGIN_NATURE_ID, monitor);
		EclipseProjectUtil.addNature(this.project, XtextProjectHelper.NATURE_ID, monitor);
		AttrCondDefLibraryProvider.syncAttrCondDefLibrary(this.project);

		this.attrConds = getDefaultAttributeConditions();
		return this.project;
	}

	public Set<AttrCondDef> getAttrConds() {
		return this.attrConds;
	}

	public IProject getProject() {
		return this.project;
	}

	/**
	 * @return
	 * @throws CoreException
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	private Set<AttrCondDef> getDefaultAttributeConditions()
			throws CoreException, IOException, FileNotFoundException {
		final ExtensionFileVisitor visitor = new ExtensionFileVisitor("tgg");
		this.project.accept(visitor);
		final List<Path> files = visitor.getFiles();
		if (files.size() != 1) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PLUGIN_ID,
					"Couldn't find eMoflon attribure constraint specifications!"));
		}
		final Path path = files.get(0);
		final Path workspaceRelativePath = this.project.getWorkspace().getRoot().getLocation().toFile().toPath()
				.relativize(path);
		final Resource attrCondResource = this.resourceSet
				.createResource(URI.createPlatformResourceURI(workspaceRelativePath.toString(), true));
		attrCondResource.load(new FileInputStream(path.toFile()), Collections.emptyMap());
		final EList<AttrCondDef> attributeCondDefs = ((TripleGraphGrammarFile) attrCondResource.getContents().get(0))
				.getLibrary().getAttributeCondDefs();
		return new HashSet<>(attributeCondDefs);
	}

	public void addMoreAttrConds(Collection<AttrCondDef> defs) {
		this.attrConds.addAll(defs);
	}
}
