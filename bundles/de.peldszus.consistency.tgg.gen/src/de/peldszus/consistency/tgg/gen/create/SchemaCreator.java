package de.peldszus.consistency.tgg.gen.create;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.emoflon.ibex.tgg.ide.admin.IbexTGGNature;
import org.moflon.tgg.mosl.tgg.AttrCondDef;
import org.moflon.tgg.mosl.tgg.CorrType;
import org.moflon.tgg.mosl.tgg.Import;
import org.moflon.tgg.mosl.tgg.Schema;
import org.moflon.tgg.mosl.tgg.TggFactory;
import org.moflon.tgg.mosl.tgg.TripleGraphGrammarFile;

public class SchemaCreator {

	private final IProject project;
	private final ResourceSet resourceSet;

	public SchemaCreator(IProject project, ResourceSet resourceSet) {
		this.project = project;
		this.resourceSet = resourceSet;
	}

	/**
	 * @param name
	 * @param allEPackages
	 * @param collection
	 * @return
	 * @throws IOException
	 */
	public Schema createSchema(String name, final Set<EPackage> allEPackages, Set<EDataType> allEDataTypes, Set<CorrType> collection)
			throws IOException {

		final IFile schemaLocation = this.project.getFile(IbexTGGNature.SCHEMA_FILE);
		final Resource schemaResource = this.resourceSet.createResource(URI
				.createPlatformResourceURI(this.project.getName() + File.separator + IbexTGGNature.SCHEMA_FILE, true));
		final Schema schema = TggFactory.eINSTANCE.createSchema();
		schema.setName(name);
		schema.getSourceTypes().addAll(allEPackages);
		schema.getTargetTypes().addAll(allEPackages);
		schema.getCorrespondenceTypes().addAll(collection);
		final TripleGraphGrammarFile schemaFile = TggFactory.eINSTANCE.createTripleGraphGrammarFile();
		schemaFile.setSchema(schema);
		schemaFile.getImports().addAll(createEPackageImports(allEPackages));

		final Collection<AttrCondDef> attributeCondDefs = allEDataTypes.parallelStream()
				.map(data -> AttributeConditionCreator.createEqualsContitionForType(data))
				.collect(Collectors.toSet());
		schema.getAttributeCondDefs().addAll(attributeCondDefs );

		schemaResource.getContents().add(schemaFile);
		schemaResource.save(new FileOutputStream(schemaLocation.getLocation().toFile()), Collections.emptyMap());
		return schema;
	}

	/**
	 * @param allEPackages
	 * @param factory
	 * @return
	 */
	private List<Import> createEPackageImports(final Set<EPackage> allEPackages) {
		return allEPackages.parallelStream().map(ePackage -> {
			final Import i = TggFactory.eINSTANCE.createImport();
			i.setName(ePackage.eResource().getURI().toString());
			return i;
		}).collect(Collectors.toList());
	}
}
