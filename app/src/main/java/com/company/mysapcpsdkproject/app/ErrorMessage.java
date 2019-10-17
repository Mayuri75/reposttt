package com.company.mysapcpsdkproject.app;

import android.os.Bundle;

/**
 * Simple wrapper class for error messages, which are used to send notifications
 * to the {@link ErrorHandler}.
 *
 * Error messages have a (short) title and a longer description. If an exception is also
 * attached, then its stack trace will be processed and presented to the user. The final argument
 * is can indicate whether the application can still work (isFatal = false) with somewhat
 * limited functionality or it should be shut down be the error handler (isFatal = true).
 */
public class ErrorMessage {

    public static final String KEY_TITLE = "TITLE";
    public static final String KEY_DESC = "DESC";
    public static final String KEY_EX = "EX";
    public static final String KEY_ISFATAL = "ISFATAL";

    // default title and description
    private String title = "Error!";
    private String description = "";
    private Exception ex = null;
    private boolean isFatal = false;
    private Bundle errorBundle;

    /**
     * ErrorMessage constructor for non-fatal events with no exception.
     *
     * @param title short description
     * @param description longer description, explaining also the consequences of the
     *                    error
     */
    public ErrorMessage(String title, String description) {
        this(title, description, null, false);
    }

    /**
     * Error message constructor with complete customization possibilities.
     *
     * @param title short description
     * @param description longer description, explaining also the consequences of the
     *                    error
     * @param ex exception object, its stack trace will also be presented to the user
     * @param isFatal true indicates that the error is fatal,
     *                the application couldn't be continued
     */
    public ErrorMessage(String title, String description, Exception ex, boolean isFatal) {
        if (title != null && !title.isEmpty()) {
            this.title = title;
        }

        if (description != null && !description.isEmpty()) {
            this.description = description;
        }

        this.ex = ex;
        this.isFatal = isFatal;

        errorBundle = new Bundle();
        errorBundle.putString(KEY_TITLE, this.title);
        errorBundle.putString(KEY_DESC, this.description);
        if (ex != null) {
            errorBundle.putSerializable(KEY_EX, this.ex);
        }
        errorBundle.putBoolean(KEY_ISFATAL, this.isFatal);
    }

    /**
     * Returns a {@link Bundle} containing the error parameters with the self-explaining
     * keys KEY_TITLE, KEY_DESC, KEY_EX, KEY_ISFATAL, for the title, description,
     * exception object and fatality.
     *
     * @return
     */
    public Bundle getErrorBundle() {
        return  errorBundle;
    }
}

