package com.codepdbh.chavokart;

import java.io.IOException;

/** An error whose message is a localized string resource (es / en / pt-BR). */
final class UserError extends IOException {
    final int messageId;
    final Object[] args;

    UserError(int messageId, Object... args) {
        super("string#" + messageId);
        this.messageId = messageId;
        this.args = args;
    }

    static String describe(android.content.Context context, Throwable error) {
        if (error instanceof UserError) {
            UserError e = (UserError) error;
            return context.getString(e.messageId, e.args);
        }
        String message = error.getMessage();
        return context.getString(R.string.error_unexpected, message == null ? error.getClass().getSimpleName() : message);
    }
}
