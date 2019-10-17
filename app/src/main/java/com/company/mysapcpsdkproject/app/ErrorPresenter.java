package com.company.mysapcpsdkproject.app;

/**
 * This is a general interface, which is used by the {@link ErrorHandler} to present error
 * messages to the user. The default implementation is based on notification dialogs,
 * but by the implementation of this interface this can be customized by the application
 * developer.
 */
public interface ErrorPresenter {
    /**
     * This method is called by the {@link ErrorHandler} to show error messages to the
     * application's user.
     *
     * @param errorTitle short title for the error
     * @param errorDetail detailed error description, which contains also the consequences of
     *                the problem
     * @param e catched exception might also be attached
     * @param isFatal flag to indicate, whether the application could still work (maybe with
     *                limited functionality)
     */
    public void presentError(String errorTitle, String errorDetail, Exception e, boolean isFatal);
}
