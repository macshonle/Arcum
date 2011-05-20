package edu.ucsd.arcum.interpreter.ast;

// TODO: Replace with Google Supplier<T>
// EXAMPLE: Which is clearer, "create" or "get"? This class can be created when it
// would aid the documentation of the program; for rapid development, maybe the
// Supplier interface would be better: There are trade-offs. A technical issue is
// identifying only the proper uses and not just the parts of code that look like it.
// (This itself could be justification for a switch, because otherwise it's hard to
// reason about because you don't know if two Suppliers of the same type represent
// the same conceptual unit.) One way to identify "this String Supplier, but not this
// one" is to do it based on sets of methods that accept it as an argument or return
// it as a result. This leaves open internal code which could be one or the other when
// independent of the others. This could be a refactoring issue that will have to be
// left up to the programmer. A third alternative Option would force @annotations
// on the types (technical issue there: not all types, e.g. type parameters, can be
// annotated-- perhaps a special annotation on the top-level for these weird cases?)
public interface ConstructorThunk<T>
{
    public T create();
}
