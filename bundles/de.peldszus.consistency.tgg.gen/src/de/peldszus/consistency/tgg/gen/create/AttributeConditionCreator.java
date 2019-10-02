package de.peldszus.consistency.tgg.gen.create;

import java.util.Optional;
import java.util.Set;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EDataType;
import org.moflon.tgg.mosl.tgg.Adornment;
import org.moflon.tgg.mosl.tgg.AttrCondDef;
import org.moflon.tgg.mosl.tgg.AttributeExpression;
import org.moflon.tgg.mosl.tgg.ObjectVariablePattern;
import org.moflon.tgg.mosl.tgg.Param;
import org.moflon.tgg.mosl.tgg.TggFactory;

public class AttributeConditionCreator {

	private final Set<AttrCondDef> attrCondDefs;

	public AttributeConditionCreator(Set<AttrCondDef> set) {
		this.attrCondDefs = set;
	}

	public static AttrCondDef createEqualsContitionForType(EDataType eAttribute) {
		final AttrCondDef def = TggFactory.eINSTANCE.createAttrCondDef();
		def.setName("eq_" + eAttribute.getName());
		def.setUserDefined(true);
		def.getParams().add(createNewParam(eAttribute, "a"));
		def.getParams().add(createNewParam(eAttribute, "b"));
		final Adornment adornment = TggFactory.eINSTANCE.createAdornment();
		adornment.getValue().add("F");
		adornment.getValue().add("F");
		def.getAllowedSyncAdornments().add(adornment);
		return def;
	}

	/**
	 * @param type
	 * @param name
	 * @return
	 */
	private static Param createNewParam(EDataType type, String name) {
		final Param a = TggFactory.eINSTANCE.createParam();
		a.setParamName(name);
		a.setType(type);
		return a;
	}

	public AttrCondDef findEqualsConditionForType(EDataType eDataType) {
		final Optional<AttrCondDef> result = this.attrCondDefs.parallelStream().filter(def -> {
			if (!def.getName().toLowerCase().contains("eq")) {
				return false;
			}
			final EList<Param> params = def.getParams();
			if (params.size() != 2) {
				return false;
			}
			return params.get(0).getType().equals(eDataType) && params.get(1).getType().equals(eDataType);
		}).findAny();
		if (result.isPresent()) {
			return result.get();
		}
		return null;
	}

	/**
	 * @param target
	 * @param eAttribute
	 * @return
	 */
	public AttributeExpression createAttributeExpression(ObjectVariablePattern target, final EAttribute eAttribute) {
		final AttributeExpression trgParam = TggFactory.eINSTANCE.createAttributeExpression();
		trgParam.setAttribute(eAttribute);
		trgParam.setObjectVar(target);
		return trgParam;
	}

	public Set<AttrCondDef> getAttrs() {
		return this.attrCondDefs;
	}
}
