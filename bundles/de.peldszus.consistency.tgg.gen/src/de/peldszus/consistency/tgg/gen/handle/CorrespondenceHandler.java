package de.peldszus.consistency.tgg.gen.handle;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.EClass;
import org.moflon.tgg.mosl.tgg.CorrType;
import org.moflon.tgg.mosl.tgg.TggFactory;

public class CorrespondenceHandler {
	private final Map<EClass, CorrType> correspondencesMap;

	public CorrespondenceHandler(final Set<EClass> allEClasses) {
		this.correspondencesMap = allEClasses.parallelStream().map(eClass -> {
			final CorrType corr = TggFactory.eINSTANCE.createCorrType();
			corr.setName(eClass.getName() + "Corr");
			corr.setSource(eClass);
			corr.setTarget(eClass);
			return corr;
		}).collect(Collectors.toMap(corr -> corr.getSource(), corr -> corr));
	}

	public Collection<CorrType> allCorrespondences() {
		return this.correspondencesMap.values();
	}

	public CorrType getCorrespondence(EClass key) {
		return this.correspondencesMap.get(key);
	}

	public boolean containsKey(EClass key) {
		return this.correspondencesMap.containsKey(key);
	}
}