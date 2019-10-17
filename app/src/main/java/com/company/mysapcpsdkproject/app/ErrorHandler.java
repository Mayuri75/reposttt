package com.company.mysapcpsdkproject.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

/**
 * Central handler class, which processes {@link ErrorMessage} notifications received
 * from the application. The messages land in an error queue and processed one-by-one.
 * When the application is in foreground then each messages will be presented on
 * a dialog with a single 'OK' button. If the message contained an exception object,
 * then its stack trace will also be shown. If the message's fatal flag were 'true'
 * then after pressing the OK button, the application is shut down. If the application were
 * in background then the dialogs appear after it returns to foreground.
 */
public class ErrorHandler extends HandlerThread {

    private Handler handler;
    private ErrorPresenter presenter;

    /**
     * Creates an error handler.
     *
     * @param name name for the handler
     */
    public ErrorHandler(String name) {
        super(name);
    }

    /**
     * Returns the {@link ErrorPresenter} which was set for this handler.
     * @return {@link ErrorPresenter}
     */
    public ErrorPresenter getPresenter() {
        return presenter;
    }

    /**
     * This method is used to set the {@link ErrorPresenter}.
     * @param presenter {@link ErrorPresenter}
     */
    public void setPresenter(ErrorPresenter presenter) {
        this.presenter = presenter;
    }

    @Override
    protected void onLooperPrepared() {

        handler = new Handler(getLooper()) {

            @Override
            public void handleMessage(Message msg) {

                String errorTitle = msg.getData().getString(ErrorMessage.KEY_TITLE);
                String errorDescription = msg.getData().getString(ErrorMessage.KEY_DESC);
                Exception exception = (Exception) msg.getData().get(ErrorMessage.KEY_EX);
                boolean isFatal = msg.getData().getBoolean(ErrorMessage.KEY_ISFATAL);

                presenter.presentError(errorTitle, errorDescription, exception, isFatal);
            }
        };
    }

    /**
     * This method is used to send {@link ErrorMessage} objects to the handler.
     *
     * @param error {@link ErrorMessage} object containing the error information
     */
    public synchronized void sendErrorMessage(ErrorMessage error) {
        Bundle errorBundle = error.getErrorBundle();
        Message errorMessage = Message.obtain(handler);
        errorMessage.setData(errorBundle);
        handler.sendMessage(errorMessage);
    }
}
