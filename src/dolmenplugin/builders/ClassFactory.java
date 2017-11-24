package dolmenplugin.builders;

import java.io.File;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;

/**
 * Class which computes and gathers all the various useful
 * information to generate a Java compilation unit either
 * from a lexer or a grammar description: various project 
 * and resource handles, names and top-level Java elements
 * 
 * @author Stéphane Lescuyer
 */
public final class ClassFactory {

	// The project containing the Dolmen resource from which
	// a Java class should be derived
	public final IProject project;
	// The Dolmen resource from which a Java class should be derived
	public final IFile resource;
	
	// The system file associated to the Dolmen resource
	public final File file;
	// The system directory path to the Dolmen resource
	public final String dir;
	// The file name of the Dolmen resource
	public final String filename;
	
	// The unqualified name of the generated Java class
	public final String className;
	// The system file handle for the generated Java class
	public final File classFile;
	// The resource handle for the generated Java class
	public final IFile classResource;
	
	// The JDT compilation unit for the generated Java class
	public final ICompilationUnit classCU;
	// The JDT package fragment containing the generated Java class
	public final IPackageFragment classPackage;
	
	/**
	 * Builds a class factory for the given Dolmen resource
	 * 
	 * @param resource
	 */
	public ClassFactory(IFile resource) {
		this.resource = resource;
		// project can't be null because IFile cannot be the workspace root
		this.project = resource.getProject();	
		
		// Find the associated file, directory and filename
		this.file = resource.getLocation().toFile();
		this.dir = file.getParent();
		this.filename = file.getName();

		// Build the handle for the class to generate
		this.className = filename.substring(0, filename.lastIndexOf('.'));
		this.classFile = new File(dir, className + ".java");

		// Find the corresponding resource and interpret
		// it in the Java project
		String classRelativePath = projectRelativePath(project, classFile);
		this.classResource = project.getFile(classRelativePath);
		IJavaElement classElt = JavaCore.create(classResource);
		if (classElt == null)
			throw new IllegalStateException("Cannot associate Java element to " + classResource 
					+ ". Check that the project has a Java nature, and that the resource's container"
					+ " is below some Java source folder.");
		if (classElt.getElementType() != IJavaElement.COMPILATION_UNIT) {
			throw new IllegalStateException("Class resource " + classResource + " is not a compilation unit.");
		}
		this.classCU = (ICompilationUnit) classElt;
		IJavaElement packageElt = classCU.getParent();
		if (packageElt == null)
			throw new IllegalStateException("Cannot find package fragment containing " + classCU);
		if (packageElt.getElementType() != IJavaElement.PACKAGE_FRAGMENT) {
			throw new IllegalStateException("Class resource container " + packageElt + " is not a package fragment.");
		}
		this.classPackage = (IPackageFragment) packageElt;
	}

	// It doesn't seem a very robust way of doing things. 
	// Can I directly change the extension of the original resource, without going through File?
	private static String projectRelativePath(IProject project, File file) {
		String filePath = file.getPath();
		String projectDir = project.getLocation().toOSString();
		if (!filePath.startsWith(projectDir))
			throw new IllegalStateException(
				"Generated file " + filePath + " not in project " + projectDir);
		String relPath = filePath.substring(projectDir.length() + 1);
		return relPath;
	}

}
