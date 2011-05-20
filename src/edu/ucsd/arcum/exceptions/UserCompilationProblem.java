package edu.ucsd.arcum.exceptions;

public class UserCompilationProblem extends ArcumError
{
    private static final long serialVersionUID = 1L;
    private SourceLocation position;

    public UserCompilationProblem(String message) {
        super(message);
    }
    
    public UserCompilationProblem(String message, Object... args) {
        super(String.format(message, args));
    }

    public UserCompilationProblem(SourceLocation position, UserCompilationProblem ucp) {
        this(ucp.getMessage());
        this.position = position;
    }

    public SourceLocation getPosition() {
        return position;
    }
}