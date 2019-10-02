package de.peldszus.consistency.tgg.gen.create;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.emoflon.ibex.tgg.ide.admin.IbexTGGNature;
import org.moflon.tgg.mosl.tgg.AttrCond;
import org.moflon.tgg.mosl.tgg.AttrCondDef;
import org.moflon.tgg.mosl.tgg.CorrType;
import org.moflon.tgg.mosl.tgg.CorrVariablePattern;
import org.moflon.tgg.mosl.tgg.LinkVariablePattern;
import org.moflon.tgg.mosl.tgg.ObjectVariablePattern;
import org.moflon.tgg.mosl.tgg.Operator;
import org.moflon.tgg.mosl.tgg.ParamValue;
import org.moflon.tgg.mosl.tgg.Rule;
import org.moflon.tgg.mosl.tgg.Schema;
import org.moflon.tgg.mosl.tgg.TggFactory;
import org.moflon.tgg.mosl.tgg.TripleGraphGrammarFile;
import org.moflon.tgg.mosl.tgg.Using;

import de.peldszus.consistency.tgg.gen.handle.ConainerHandler;
import de.peldszus.consistency.tgg.gen.handle.CorrespondenceHandler;

/**
 * Functionalities for creating new TGG rules
 *
 * @author speldszus
 *
 */
public class RuleCreator {

	private final AttributeConditionCreator attrConds;
	private final ResourceSet resourceSet;
	private final IProject project;
	private final ConainerHandler containers;
	private final CorrespondenceHandler correspondences;

	/**
	 * Initializes the class with information needed for the creation of rules
	 *
	 * @param resourceSet     The resource set containing the Schema and into which
	 *                        the rules should be inserted
	 * @param containers      The handler providing information about containment
	 *                        edges
	 * @param correspondences The handler providing information about correspondence
	 *                        types in the schema
	 * @param projectCreator  The creator used for creating the Eclipse project into
	 *                        which the rules should be inserted
	 */
	public RuleCreator(ResourceSet resourceSet, ConainerHandler containers, CorrespondenceHandler correspondences,
			EclipseProjectCreator projectCreator) {
		this.attrConds = new AttributeConditionCreator(projectCreator.getAttrConds());
		this.resourceSet = resourceSet;
		this.correspondences = correspondences;
		this.project = projectCreator.getProject();
		this.containers = containers;
	}

	/**
	 * Creates a new rule in the given TGG file for the translation of a single type
	 *
	 * @param ruleName   The name of the new rule
	 * @param schema     The schem containing the correspondence types
	 * @param ruleFile   The rule file into which the rule should be insterted
	 * @param createType The type to translate
	 * @return The created rule
	 */
	private Rule createCreateRule(String ruleName, Schema schema, TripleGraphGrammarFile ruleFile, EClass createType) {
		final Rule rule = TggFactory.eINSTANCE.createRule();
		rule.setName(ruleName);
		rule.setSchema(schema);
		ruleFile.getRules().add(rule);

		final CorrVariablePattern corr = createMapping(createType, rule, true, "");

		final ObjectVariablePattern source = corr.getSource();
		final ObjectVariablePattern target = corr.getTarget();
		final EList<AttrCond> attrConditions = rule.getAttrConditions();
		attrConditions.addAll(createType.getEAllAttributes().parallelStream().map(eAttribute -> {
			final AttrCondDef attrCond = this.attrConds.findEqualsConditionForType(eAttribute.getEAttributeType());
			final AttrCond condition = TggFactory.eINSTANCE.createAttrCond();
			final EList<ParamValue> parameters = condition.getValues();
			parameters.add(this.attrConds.createAttributeExpression(source, eAttribute));
			parameters.add(this.attrConds.createAttributeExpression(target, eAttribute));

			condition.setName(attrCond);
			return condition;
		}).collect(Collectors.toList()));
		return rule;
	}

	/**
	 *
	 * Creates a new rule for translating the given EReference
	 *
	 * @param eReference A EReference
	 * @param schema     The schema containing the correspondence types
	 * @throws IOException If the rule couldn't be stored
	 */
	public void createLinkRule(EReference eReference, Schema schema) throws IOException {
		final EClass trgType = (EClass) eReference.getEType();
		final EClass srcType = eReference.getEContainingClass();
		final String ruleName = srcType.getName() + "LinkTo" + trgType.getName() + "With" + eReference.getName();
		final TripleGraphGrammarFile ruleFile = createRuleFile(ruleName, schema);
		final Rule rule = TggFactory.eINSTANCE.createRule();
		rule.setName(ruleName);
		rule.setSchema(schema);
		ruleFile.getRules().add(rule);

		final CorrVariablePattern src = createMapping(srcType, rule, false, "Source");
		final CorrVariablePattern trg = createMapping(trgType, rule, false, "Target");
		createNewLink(src.getSource(), trg.getSource(), eReference);
		createNewLink(src.getTarget(), trg.getTarget(), eReference);

		save(ruleFile);
	}

	/**
	 * @param schema
	 * @param ruleFile
	 * @throws IOException
	 */
	private void save(final TripleGraphGrammarFile ruleFile) throws IOException {
		final Resource ruleResource = ruleFile.eResource();
		final Map<TripleGraphGrammarFile, Resource> schemaMap = ruleFile.getRules().parallelStream()
				.map(rule -> rule.getSchema()).filter(Objects::nonNull)
				.map(schema -> (TripleGraphGrammarFile) schema.eContainer()).distinct()
				.collect(Collectors.toMap(file -> file, file -> file.eResource()));
		ruleResource.getContents().addAll(schemaMap.keySet());

		final Map<TripleGraphGrammarFile, Resource> attrMap = this.attrConds.getAttrs().parallelStream()
				.map(def -> (TripleGraphGrammarFile) def.eContainer().eContainer()).distinct()
				.collect(Collectors.toMap(file -> file, file -> file.eResource()));
		ruleResource.getContents().addAll(attrMap.keySet());

		try {
			ruleResource.save(Collections.emptyMap());
		} catch (final RuntimeException e) {
			e.printStackTrace();
		}
		schemaMap.entrySet().parallelStream().forEach(entry -> entry.getValue().getContents().add(entry.getKey()));
		attrMap.entrySet().parallelStream().forEach(entry -> entry.getValue().getContents().add(entry.getKey()));
	}

	/**
	 *
	 * @param createClass
	 * @param schema
	 * @throws IOException
	 */
	public void createRuleFile(EClass createClass, Schema schema) throws IOException {
		final String createClassName = createClass.getName();
		final TripleGraphGrammarFile ruleFile = createRuleFile(createClassName, schema);

		final Set<EReference> references = this.containers.getAllContainerReferences(createClass);
		if (references.isEmpty()) {
			createCreateRule(createClassName, schema, ruleFile, createClass);
		} else {
			for (final EReference containment : references) {
				final EClass eContainer = containment.getEContainingClass();
				String ruleName;
				if (references.size() == 1) {
					ruleName = createClassName;
				} else {
					ruleName = createClassName + "_" + containment.getName() + "_" + eContainer.getName();
				}
				final Rule rule = createCreateRule(ruleName, schema, ruleFile, createClass);
				final CorrVariablePattern contextCorr = createMapping(eContainer, rule, false, "Context");

				createNewLink(contextCorr.getSource(), rule.getSourcePatterns().get(0), containment);
				createNewLink(contextCorr.getTarget(), rule.getTargetPatterns().get(0), containment);
			}
		}
		save(ruleFile);
	}

	/**
	 * @param name
	 * @param schema
	 * @return
	 */
	private TripleGraphGrammarFile createRuleFile(final String name, Schema schema) {
		final IPath tggFile = this.project.getFile(IbexTGGNature.SCHEMA_FILE).getParent().getProjectRelativePath()
				.append("rules/" + name + ".tgg");
		final Resource ruleResource = this.resourceSet.createResource(
				URI.createPlatformResourceURI(this.project.getName() + File.separator + tggFile.toString(), true));
		final TripleGraphGrammarFile ruleFile = TggFactory.eINSTANCE.createTripleGraphGrammarFile();
		ruleResource.getContents().add(ruleFile);
		addUse(ruleFile, schema.getName() + ".*");
		addUse(ruleFile, "AttrCondDefLibrary.*");
		return ruleFile;
	}

	/**
	 * @param ruleFile
	 * @param nameSpace
	 */
	private void addUse(final TripleGraphGrammarFile ruleFile, String nameSpace) {
		final Using use = TggFactory.eINSTANCE.createUsing();
		use.setImportedNamespace(nameSpace);
		ruleFile.getUsing().add(use);
	}

	/**
	 *
	 * @param create
	 * @param createClass
	 * @param rule
	 * @param postfix
	 * @return
	 * @return
	 */
	private CorrVariablePattern createMapping(EClass createClass, Rule rule, boolean create, String postfix) {
		final ObjectVariablePattern src = createVariable(createClass, create, "s", postfix);
		rule.getSourcePatterns().add(src);

		final ObjectVariablePattern trg = createVariable(createClass, create, "t", postfix);
		rule.getTargetPatterns().add(trg);

		final CorrType corrType = this.correspondences.getCorrespondence(createClass);
		String corrPostFix = postfix;
		if (!create) {
			corrPostFix += "Context";
		}
		final CorrVariablePattern corr = createCorrespondence(src, trg, corrType, create, corrPostFix);
		rule.getCorrespondencePatterns().add(corr);

		if (create) {
			final List<CorrVariablePattern> parentCorr = createClass.getEAllSuperTypes().parallelStream()
					.filter(parent -> this.correspondences.containsKey(parent)).map(parent -> {
						final CorrType parentCorrType = this.correspondences.getCorrespondence(parent);
						return createCorrespondence(src, trg, parentCorrType, true, "");
					}).collect(Collectors.toList());
			rule.getCorrespondencePatterns().addAll(parentCorr);
		}

		return corr;
	}

	/**
	 * @param target
	 * @param objectVariablePattern
	 * @param containment
	 * @return
	 */
	private LinkVariablePattern createNewLink(final ObjectVariablePattern source, ObjectVariablePattern target,
			final EReference containment) {
		final LinkVariablePattern link = TggFactory.eINSTANCE.createLinkVariablePattern();
		link.setType(containment);
		link.setTarget(target);
		link.setOp(getCreateOperator());
		source.getLinkVariablePatterns().add(link);
		return link;
	}

	/**
	 * @param src
	 * @param trg
	 * @param type
	 * @param create
	 * @return
	 */
	private CorrVariablePattern createCorrespondence(final ObjectVariablePattern src, final ObjectVariablePattern trg,
			final CorrType type, boolean create, String postfix) {
		final CorrVariablePattern corr = TggFactory.eINSTANCE.createCorrVariablePattern();
		final String corrName = "corr" + type.getName() + postfix;
		if (create) {
			corr.setOp(getCreateOperator());
		}
		corr.setName(corrName);
		corr.setType(type);
		corr.setSource(src);
		corr.setTarget(trg);
		return corr;
	}

	/**
	 * @param type
	 * @param create
	 * @param prefix
	 * @param postfix
	 * @return
	 */
	private ObjectVariablePattern createVariable(EClass type, boolean create, String prefix, String postfix) {
		final ObjectVariablePattern trg = TggFactory.eINSTANCE.createObjectVariablePattern();
		final String targetName = prefix + type.getName() + postfix;
		if (create) {
			trg.setOp(getCreateOperator());
		}
		trg.setName(targetName);
		trg.setType(type);
		return trg;
	}

	private Operator getCreateOperator() {
		final Operator operator = TggFactory.eINSTANCE.createOperator();
		operator.setValue("++");
		return operator;
	}
}
