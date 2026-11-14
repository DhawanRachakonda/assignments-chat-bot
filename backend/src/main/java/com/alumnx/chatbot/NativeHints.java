package com.alumnx.chatbot;

import java.io.IOException;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * GraalVM native-image hints for classes only used reflectively: our JSON records, the {@code @Tool}
 * methods the model invokes, and the OpenAI SDK's model classes, which Jackson (de)serializes
 * reflectively and which the SDK does not ship hints for.
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(NativeHints.Registrar.class)
@RegisterReflectionForBinding({
        Entry.class,
        EntriesClient.Page.class,
        AssignmentTools.Submission.class,
        ChatController.ChatRequest.class,
        AuthController.LoginRequest.class,
        AuthController.LoginResponse.class
})
class NativeHints {

    static class Registrar implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.reflection().registerType(AssignmentTools.class,
                    MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_DECLARED_METHODS);

            // Only the OpenAI SDK packages the chat-completions path (de)serializes. Registering the whole
            // SDK (images, files, responses, ...) runs native-image out of memory.
            String[] patterns = {
                    "classpath*:com/openai/models/chat/**/*.class",
                    "classpath*:com/openai/models/completions/*.class",
                    "classpath*:com/openai/models/*.class",
                    "classpath*:com/openai/core/*.class",
                    "classpath*:com/openai/errors/*.class"
            };
            try {
                var resolver = new PathMatchingResourcePatternResolver(classLoader);
                var resources = new java.util.ArrayList<Resource>();
                for (String p : patterns) resources.addAll(java.util.Arrays.asList(resolver.getResources(p)));
                for (Resource r : resources) {
                    String path = r.getURL().toString();
                    // jar URLs look like ...repository/com/openai/x.jar!/com/openai/Foo.class; take what follows "!/".
                    int bang = path.indexOf("!/");
                    int i = bang >= 0 ? bang + 2 : path.lastIndexOf("com/openai/");
                    String name = path.substring(i, path.length() - ".class".length()).replace('/', '.');
                    if (name.endsWith("module-info") || name.endsWith("package-info")) continue;
                    hints.reflection().registerType(TypeReference.of(name),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_DECLARED_METHODS,
                            MemberCategory.ACCESS_DECLARED_FIELDS);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Could not scan OpenAI SDK classes for native hints", e);
            }
        }
    }
}
