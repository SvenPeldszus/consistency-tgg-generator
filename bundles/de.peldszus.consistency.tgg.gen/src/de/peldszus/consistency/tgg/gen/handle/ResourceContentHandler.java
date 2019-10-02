package de.peldszus.consistency.tgg.gen.handle;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EPackage.Registry;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.impl.BasicEObjectImpl;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.resource.XtextResourceSet;

/**
 * This class searches and stores all relevant elements from the EPackages
 *
 * @author speldszus
 *
 */
public class ResourceContentHandler {

	private final Set<EPackage> allEPackages;
	private final Set<EClass> allEClasses;
	private final Set<EReference> allEReferences;
	private final Set<EDataType> allEDataTypes;
	private final XtextResourceSet resourceSet;
	private final Map<EPackage, URI> uriMap;

	/**
	 * Creates an new handler for a set of EPackages
	 *
	 * @param ePackages The EPackages which should be made accessible
	 */
	public ResourceContentHandler(Collection<EPackage> ePackages) {
		this.uriMap = new HashMap<>();
		this.allEPackages = new HashSet<>();
		this.resourceSet = initResourceSet(ePackages);
		this.allEClasses = new HashSet<>();
		this.allEReferences = new HashSet<>();
		this.allEDataTypes = new HashSet<>();
		initAllRelevantElements(this.allEPackages);
	}

	/**
	 * Initializes an XtextResourceSet
	 *
	 * @param ePackages The EPackages to add to the resource set
	 *
	 * @return The ResourceSet
	 */
	private XtextResourceSet initResourceSet(Collection<EPackage> ePackages) {
		final Set<EPackage> allEPackagesTmp = getAllEPackages(ePackages);
		final XtextResourceSet set = new XtextResourceSet();
		final Registry registry = set.getPackageRegistry();
		final Set<URI> uris = new HashSet<>(allEPackagesTmp.size());
		for (final EPackage ePackage : allEPackagesTmp) {
			final Resource eResource = ePackage.eResource();
			final URI uri = eResource.getURI();
			uris.add(uri);
			registry.put(uri.toString(), ePackage);
			registry.put(ePackage.getNsURI(), ePackage);
			if(this.uriMap.containsKey(ePackage)) {
				registry.put(this.uriMap.get(ePackage).toString(), ePackage);
			}
		}
		EcoreUtil.resolveAll(set);
		for (final URI uri : uris) {
			this.allEPackages.add((EPackage) set.getResource(uri, true).getContents().get(0));
		}
		return set;
	}

	/**
	 * The initial and all referenced EPackages
	 *
	 * @return A set of EPackages
	 */
	public Set<EPackage> getAllEPackages() {
		return this.allEPackages;
	}

	/**
	 * All EClasses contained in the initial and references EPackages
	 *
	 * @return A set of EClasses
	 */
	public Set<EClass> getAllEClasses() {
		return this.allEClasses;
	}

	/**
	 * All EDataTypes contained in the initial and references EPackages
	 *
	 * @return A set of EDataTypes
	 */
	public Set<EDataType> getAllEDataTypes() {
		return this.allEDataTypes;
	}

	/**
	 * All EReferences contained in the initial and references EPackages
	 *
	 * @return A set of EReferences
	 */
	public Set<EReference> getAllEReferences() {
		return this.allEReferences;
	}

	/**
	 * Collects all used EPackages, EClasses and EReferences
	 *
	 * @param ePackages The EPackages for which all used elements should be
	 *                  collected
	 */
	private void initAllRelevantElements(Collection<EPackage> ePackages) {
		ePackages.parallelStream().flatMap(ePackage -> ePackage.eContents().parallelStream()).forEach(eClassifier -> {
			if (eClassifier instanceof EDataType) {
				this.allEDataTypes.add((EDataType) eClassifier);
			} else if (eClassifier instanceof EClass) {
				final EClass eClass = (EClass) eClassifier;
				this.allEClasses.add(eClass);
				final EList<EReference> eReferences = eClass.getEReferences();
				for (final EReference eReference : eReferences) {
					final EClass eType = eReference.getEReferenceType();
					if (eType.eIsProxy()) {
						eReference.setEType(resolveProxyClass(eType));
					}
				}
				this.allEReferences.addAll(eReferences);
			}
		});
	}

	/**
	 * Resolves the proxy class
	 *
	 * @param proxy A proxy
	 * @return The resolved class or the proxy if the resolve wasn't successful
	 */
	private EClass resolveProxyClass(EClass proxy) {
		URI uri = ((BasicEObjectImpl) proxy).eProxyURI();
		if (uri.hasFragment()) {
			uri = uri.trimFragment();
		}
		final Resource resource = this.resourceSet.getResource(uri, true);
		proxy = (EClass) EcoreUtil.resolve(proxy, resource);
		if (proxy.eIsProxy()) {
			final EPackage eResolvedPackage = (EPackage) resource.getContents().get(0);
			String name = proxy.getName();
			if (name == null) {
				uri = ((BasicEObjectImpl) proxy).eProxyURI();
				if (uri.hasFragment()) {
					name = uri.fragment();
					if (name.startsWith("//")) {
						name = name.substring(2);
					}
					final EClass resolved = (EClass) eResolvedPackage.getEClassifier(name);
					if (resolved != null) {
						proxy = resolved;
					}
				}
			}
		}
		return proxy;
	}

	/**
	 * Get all EPackages including the initial EPackages
	 *
	 * @param ePackages A set of EPackages
	 * @return The initial EPackages and the referenced ones
	 */
	private Set<EPackage> getAllEPackages(Collection<EPackage> ePackages) {
		final Set<EPackage> packages = new HashSet<>(ePackages);
		for (final EPackage ePackage : ePackages) {
			EcoreUtil.resolveAll(ePackage);
			this.uriMap.put(ePackage, ePackage.eResource().getURI());
			final Set<EPackage> newPackages = ePackage.getEClassifiers().parallelStream()
					.filter(eClassifier -> eClassifier instanceof EClass)
					.flatMap(eClass -> getAllEPackages(ePackage, (EClass)eClass).parallelStream())
					.collect(Collectors.toSet());
			packages.addAll(newPackages);
		}
		return packages;
	}

	/**
	 * Gets all EPackages referenced by the class
	 *
	 * @param ePackage The EPackage containing the class
	 * @param eClassifier The class
	 * @return The referenced EPackages
	 */
	public Set<EPackage> getAllEPackages(final EPackage ePackage, final EClass eClassifier) {
		final Set<EPackage> ePackages = new HashSet<>();
		for (final EReference eReference : eClassifier.getEReferences()) {
			final EClass trgEClass = eReference.getEReferenceType();
			if (trgEClass.eIsProxy()) {
				final EPackage resolvedEPackage = resolveEPackage(ePackage, trgEClass);
				eReference.setEType((EClassifier) EcoreUtil.resolve(trgEClass, resolvedEPackage));
				ePackages.add(resolvedEPackage);
			} else {
				final EPackage trgEPackage = trgEClass.getEPackage();
				if (EcorePackage.eINSTANCE != trgEPackage) {
					ePackages.add(trgEPackage);
				}
			}
		}
		return ePackages;
	}

	/**
	 * Resolves an proxy based on a known EPackage
	 *
	 * @param knownPackage A known EPackage
	 * @param proxy        The proxy
	 * @return The EPackage containing the resolved proxy
	 */
	private EPackage resolveEPackage(final EPackage knownPackage, final EClass proxy) {
		URI uri = ((BasicEObjectImpl) proxy).eProxyURI();
		if (uri.hasFragment()) {
			uri = uri.trimFragment();
		}
		final URI value = uri;
		final Optional<EPackage> result = this.uriMap.entrySet().parallelStream()
				.filter(entry -> entry.getValue().equals(value)).map(entry -> entry.getKey()).findAny();
		if (result.isPresent()) {
			return result.get();
		}
		final ResourceSet localResourceSet = knownPackage.eResource().getResourceSet();
		Resource unresolvedResource = localResourceSet.getResource(uri, true);
		EList<EObject> contents = unresolvedResource.getContents();
		if (contents.isEmpty()) {
			unresolvedResource.unload();
			if (uri.isPlatformResource()) {
				final URI resolvedUri = URI.createPlatformPluginURI(uri.toPlatformString(true), true);
				unresolvedResource = localResourceSet.getResource(resolvedUri, true);
				contents = unresolvedResource.getContents();
			}
		}
		final EPackage eResolvedPackage = (EPackage) contents.get(0);
		this.uriMap.put(eResolvedPackage, uri);
		return eResolvedPackage;
	}

	/**
	 * A getter for the xtext resource set
	 *
	 * @return the resource set
	 */
	public XtextResourceSet getResourceSet() {
		return this.resourceSet;
	}
}