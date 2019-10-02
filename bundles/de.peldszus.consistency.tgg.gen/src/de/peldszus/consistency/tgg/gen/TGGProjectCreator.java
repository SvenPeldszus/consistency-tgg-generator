package de.peldszus.consistency.tgg.gen;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.gravity.eclipse.importer.DuplicateProjectNameException;
import org.moflon.tgg.mosl.tgg.Schema;

import de.peldszus.consistency.tgg.gen.create.EclipseProjectCreator;
import de.peldszus.consistency.tgg.gen.create.RuleCreator;
import de.peldszus.consistency.tgg.gen.create.SchemaCreator;
import de.peldszus.consistency.tgg.gen.handle.CorrespondenceHandler;
import de.peldszus.consistency.tgg.gen.handle.ResourceContentHandler;

/**
 *
 * @author speldszus
 *
 */
public class TGGProjectCreator {

	private IProject project;

	/**
	 * Create a new TGG project for translating the given meta models
	 *
	 * @param ePackages The selected meta models
	 * @param name The desired project name
	 * @param monitor A progress monitor
	 * @throws DuplicateProjectNameException
	 * @throws CoreException
	 * @throws IOException
	 */
	public void createTGGProject(Collection<EPackage> ePackages, String name, IProgressMonitor monitor)
			throws DuplicateProjectNameException, CoreException, IOException {
		final ResourceContentHandler packageElements = new ResourceContentHandler(ePackages);
		final EclipseProjectCreator projectCreator = new EclipseProjectCreator(packageElements.getResourceSet());
		this.project = projectCreator.createTGGProject(name, monitor);
		create(projectCreator, packageElements);
	}

	/**
	 * Generated all required TGG files
	 *
	 * @param projectCreator The creator used to create the Eclipse project
	 * @param packageElements The handler used to discover all required elements
	 * @throws IOException If serializing a rule failed
	 */
	private void create(EclipseProjectCreator projectCreator, ResourceContentHandler packageElements) throws IOException  {
		final Set<EClass> allEClasses = packageElements.getAllEClasses();
		final XtextResourceSet resourceSet = packageElements.getResourceSet();
		final CorrespondenceHandler correspondences = new CorrespondenceHandler(allEClasses);
		final Schema schema = new SchemaCreator(this.project, resourceSet).createSchema(this.project.getName(),
				packageElements.getAllEPackages(), packageElements.getAllEDataTypes(), new HashSet<>(correspondences.allCorrespondences()));
		projectCreator.addMoreAttrConds(schema.getAttributeCondDefs());

		final RuleCreator ruleCreator = new RuleCreator(packageElements, correspondences, projectCreator);
		for (final EClass eClass : allEClasses) {
			if (eClass.isAbstract() || eClass.isInterface()) {
				continue;
			}
			ruleCreator.createRuleFile(eClass, schema);
		}

		final HashSet<EReference> seen = new HashSet<>();
		for (final EReference eReference : packageElements.getAllEReferences()) {
			if (!eReference.isContainer() && !eReference.isContainment() && !seen.contains(eReference)) {
				ruleCreator.createLinkRule(eReference, schema);
				seen.add(eReference);
				final EReference opposite = eReference.getEOpposite();
				if (opposite != null) {
					seen.add(opposite);
				}
			}
		}
	}
}
