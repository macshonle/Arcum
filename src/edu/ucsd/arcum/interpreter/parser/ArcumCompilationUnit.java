package edu.ucsd.arcum.interpreter.parser;

import static org.eclipse.jdt.core.IPackageFragment.DEFAULT_PACKAGE_NAME;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.internal.core.CompilationUnit;
import org.eclipse.jdt.internal.core.DefaultWorkingCopyOwner;
import org.eclipse.jdt.internal.core.PackageFragment;

// A .arcum source file. Uses a technique similar to AJCompilationUnit to
// pretend that it has Java syntax.
public class ArcumCompilationUnit extends CompilationUnit
{
    public ArcumCompilationUnit(String name, IProject root) {
        super(defaultPackage(root), name, DefaultWorkingCopyOwner.PRIMARY);
    }

    private static PackageFragment defaultPackage(IProject proj) {
        IJavaProject jp = JavaCore.create(proj);
        try {
            IPackageFragment[] packageFragments = jp.getPackageFragments();
            for (IPackageFragment frag: packageFragments) {
                if (frag.getElementName().equals(DEFAULT_PACKAGE_NAME))
                    return (PackageFragment)frag;
            }
            return null;
        }
        catch (JavaModelException e) {
            e.printStackTrace();
            System.out.printf("Yikes!%n");
            return null;
        }
    }
}
