package cinecli.admin.ui;

/** Closed result of one terminal read. */
public sealed interface TerminalInput
        permits SubmittedLine, GlobalCommand, EndOfInput, InputFailure {
}
