package com.baran3575.vergconnector.remapper;

import org.objectweb.asm.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class ASMStringReplacer {
    private static final Pattern INTERMEDIARY_PATTERN = Pattern.compile("(?:Lnet/minecraft/class_\\d+(?:\\$class_\\d+)*;|net[/.]minecraft[/.]class_\\d+(?:\\$class_\\d+)*|class_\\d+|method_\\d+|field_\\d+)");

    public static String replaceIntermediary(String input, Map<String, String> replacements) {
        if (input == null || input.isEmpty()) return input;
        Matcher m = INTERMEDIARY_PATTERN.matcher(input);
        if (!m.find()) return input;
        
        m.reset();
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String match = m.group();
            String replacement = replacements.get(match);
            if (replacement != null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(match));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static void processJar(Path jarFile, Map<String, String> replacements) throws IOException {
        System.out.println("[Verg Connector] ASMStringReplacer: Processing jar " + jarFile.getFileName());
        try (FileSystem fs = FileSystems.newFileSystem(java.net.URI.create("jar:" + jarFile.toUri()), Collections.singletonMap("create", "false"))) {
            for (Path root : fs.getRootDirectories()) {
                List<Path> files = Files.walk(root)
                        .filter(p -> {
                            String s = p.toString();
                            return s.endsWith(".class") || s.endsWith(".json");
                        })
                        .collect(java.util.stream.Collectors.toList());
                
                for (Path p : files) {
                    try {
                        if (p.toString().endsWith(".class")) {
                            byte[] bytes = Files.readAllBytes(p);
                            ClassReader cr = new ClassReader(bytes);
                            ClassWriter cw = new ClassWriter(0);
                            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                                @Override
                                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                                    return new MethodVisitor(Opcodes.ASM9, mv) {
                                        @Override
                                        public void visitLdcInsn(Object value) {
                                            if (value instanceof String) {
                                                String str = (String) value;
                                                String replaced = replaceIntermediary(str, replacements);
                                                super.visitLdcInsn(replaced);
                                            } else {
                                                super.visitLdcInsn(value);
                                            }
                                        }
                                        
                                        @Override
                                        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                                            return new AnnotationReplacer(super.visitAnnotation(descriptor, visible), replacements);
                                        }
                                    };
                                }
                                
                                @Override
                                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                                    return new AnnotationReplacer(super.visitAnnotation(descriptor, visible), replacements);
                                }
                                
                                @Override
                                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                                    return new FieldVisitor(Opcodes.ASM9, super.visitField(access, name, descriptor, signature, value)) {
                                        @Override
                                        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                                            return new AnnotationReplacer(super.visitAnnotation(descriptor, visible), replacements);
                                        }
                                    };
                                }
                            };
                            cr.accept(cv, 0);
                            byte[] newBytes = cw.toByteArray();
                            if (!Arrays.equals(bytes, newBytes)) {
                                Files.write(p, newBytes, StandardOpenOption.TRUNCATE_EXISTING);
                            }
                        } else if (p.toString().endsWith(".json")) {
                            String original = Files.readString(p, java.nio.charset.StandardCharsets.UTF_8);
                            String replaced = replaceIntermediary(original, replacements);
                            if (!original.equals(replaced)) {
                                Files.writeString(p, replaced, java.nio.charset.StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[Verg Connector] ASMStringReplacer Error processing file " + p + ": " + e.getMessage());
                    }
                }
            }
        }
    }
    
    private static class AnnotationReplacer extends AnnotationVisitor {
        private final Map<String, String> replacements;
        public AnnotationReplacer(AnnotationVisitor av, Map<String, String> replacements) {
            super(Opcodes.ASM9, av);
            this.replacements = replacements;
        }
        @Override
        public void visit(String name, Object value) {
            if (value instanceof String) {
                super.visit(name, replaceIntermediary((String) value, replacements));
            } else if (value instanceof Type) {
                Type type = (Type) value;
                String desc = type.getDescriptor();
                String replacedDesc = replaceIntermediary(desc, replacements);
                if (!desc.equals(replacedDesc)) {
                    super.visit(name, Type.getType(replacedDesc));
                } else {
                    super.visit(name, value);
                }
            } else {
                super.visit(name, value);
            }
        }
        @Override
        public AnnotationVisitor visitAnnotation(String name, String descriptor) {
            return new AnnotationReplacer(super.visitAnnotation(name, descriptor), replacements);
        }
        @Override
        public AnnotationVisitor visitArray(String name) {
            return new AnnotationReplacer(super.visitArray(name), replacements);
        }
    }
}
