package com.unboxy.gamemanagerservice.utils;

import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

public class UserUtils {
    public static final String USER_ID = "userId";

    public static String getUserS3Path(ContextView context, String s3Path) {
        String userId = context.get(USER_ID);

        return String.format(s3Path + "user_" + userId + "/");
    }

    public static String getUserId(ContextView context) {
        return context.get(USER_ID);
    }

    public static Mono<String> getCurrentUserId() {
        return Mono.deferContextual(ctx -> Mono.just(ctx.get(USER_ID)));
    }
}
