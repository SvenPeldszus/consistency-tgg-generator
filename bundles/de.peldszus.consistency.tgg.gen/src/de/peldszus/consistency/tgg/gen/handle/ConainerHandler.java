package de.peldszus.consistency.tgg.gen.handle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EReference;

public class ConainerHandler {
	private final Map<EClassifier, Set<EReference>> containers;

	public ConainerHandler(Set<EClass> allEClasses) {
		this.containers = new HashMap<>();
		for (final EClass eClass : allEClasses) {
			for (final EReference eReference : eClass.getEReferences()) {
				if (eReference.isContainment()) {
					add(this.containers, eReference.getEReferenceType(), eReference);
				}
			}
		}
	}

	/**
	 * @param eClass
	 * @return
	 */
	public Set<EReference> getAllContainerReferences(EClass eClass) {
		final Set<EReference> set = getContainingReferences(eClass);
		final Stream<EReference> thisContainers;
		if (set == null) {
			thisContainers = Stream.empty();
		} else {
			thisContainers = set.parallelStream();
		}
		final Stream<EReference> parentContainers = eClass.getEAllSuperTypes().parallelStream()
				.filter(parent -> hasContainingReference(parent))
				.flatMap(parent -> getContainingReferences(parent).parallelStream());
		return Stream.concat(thisContainers, parentContainers).collect(Collectors.toSet());
	}

	/**
	 * @param map
	 * @param value
	 * @param key
	 */
	private static <K, V> void add(final Map<K, Set<V>> map, final K key, final V value) {
		Set<V> refs = map.get(key);
		if (refs == null) {
			refs = new HashSet<>();
			map.put(key, refs);
		}
		refs.add(value);
	}

	public Set<EReference> getContainingReferences(EClass eClass) {
		return this.containers.get(eClass);
	}

	public boolean hasContainingReference(EClass eClass) {
		return this.containers.containsKey(eClass);
	}
}