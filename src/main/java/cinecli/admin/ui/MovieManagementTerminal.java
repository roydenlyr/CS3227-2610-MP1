package cinecli.admin.ui;

/** Fallible terminal boundary used by the Movie-management workflow. */
public interface MovieManagementTerminal {
    /** Reads one typed input result. */
    TerminalInput readLine();

    /** Writes and flushes one complete output block. */
    boolean write(String text);

    /** Best-effort writes and flushes one complete error block. */
    boolean writeError(String text);
}
