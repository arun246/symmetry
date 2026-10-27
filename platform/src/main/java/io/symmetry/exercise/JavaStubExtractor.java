package io.symmetry.exercise;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.stmt.BlockStmt;

import io.symmetry.contract.MessageSigner;

@Component
public class JavaStubExtractor implements StubExtractor {

    private static final String TODO_BODY = "{ throw new UnsupportedOperationException(\"TODO\"); }";

    @Override
    public String language() {
        return "java";
    }

    @Override
    public List<SymbolView> symbols(String source) {
        CompilationUnit unit = parse(source);
        List<SymbolView> symbols = new ArrayList<>();
        unit.findAll(MethodDeclaration.class).stream()
                .filter(method -> method.getBody().isPresent())
                .forEach(method -> symbols.add(view(method, "METHOD")));
        unit.findAll(ConstructorDeclaration.class).stream()
                .filter(constructor -> constructor.getBody() != null)
                .forEach(constructor -> symbols.add(view(constructor, "CONSTRUCTOR")));
        return symbols.stream()
                .sorted((left, right) -> Integer.compare(left.startLine(), right.startLine()))
                .toList();
    }

    @Override
    public StubExtraction extract(String source, ExtractStubRequest request) {
        CompilationUnit unit = parse(source);
        Optional<MethodDeclaration> method = unit.findAll(MethodDeclaration.class).stream()
                .filter(candidate -> candidate.getNameAsString().equals(request.symbolName()))
                .filter(candidate -> candidate.getRange().map(range -> range.begin.line == request.startLine()).orElse(false))
                .findFirst();
        if (method.isPresent()) {
            return extract(source, method.get(), "METHOD");
        }
        Optional<ConstructorDeclaration> constructor = unit.findAll(ConstructorDeclaration.class).stream()
                .filter(candidate -> candidate.getNameAsString().equals(request.symbolName()))
                .filter(candidate -> candidate.getRange().map(range -> range.begin.line == request.startLine()).orElse(false))
                .findFirst();
        if (constructor.isPresent()) {
            return extract(source, constructor.get(), "CONSTRUCTOR");
        }
        throw new IllegalArgumentException("Symbol '" + request.symbolName()
                + "' was not found at line " + request.startLine());
    }

    private StubExtraction extract(String source, BodyDeclaration<?> declaration, String kind) {
        BlockStmt body = bodyOf(declaration);
        if (body == null) {
            throw new IllegalArgumentException("Selected symbol has no body to remove");
        }
        Range bodyRange = body.getRange()
                .orElseThrow(() -> new IllegalArgumentException("Selected symbol has no range"));
        Range declarationRange = declaration.getRange()
                .orElseThrow(() -> new IllegalArgumentException("Selected symbol has no range"));

        int bodyStart = offset(source, bodyRange.begin);
        int bodyEnd = offset(source, bodyRange.end) + 1;
        String stub = source.substring(0, bodyStart) + TODO_BODY + source.substring(bodyEnd);
        parse(stub);

        String name = declaration instanceof MethodDeclaration method
                ? method.getNameAsString()
                : ((ConstructorDeclaration) declaration).getNameAsString();
        int startLine = declarationRange.begin.line;
        int endLine = declarationRange.end.line;
        String original = source.substring(offset(source, declarationRange.begin),
                offset(source, declarationRange.end) + 1);
        return new StubExtraction(name, kind, startLine, endLine, stub, MessageSigner.sha256(original));
    }

    private static SymbolView view(BodyDeclaration<?> declaration, String kind) {
        Range range = declaration.getRange()
                .orElseThrow(() -> new IllegalArgumentException("Symbol has no range"));
        BlockStmt body = bodyOf(declaration);
        if (body == null) {
            throw new IllegalArgumentException("Symbol has no body");
        }
        Range bodyRange = body.getRange()
                .orElseThrow(() -> new IllegalArgumentException("Symbol has no body range"));
        String name = declaration instanceof MethodDeclaration method
                ? method.getNameAsString()
                : ((ConstructorDeclaration) declaration).getNameAsString();
        return new SymbolView(name, kind, range.begin.line, range.end.line,
                bodyRange.begin.line, bodyRange.end.line, signature(declaration));
    }

    private static String signature(BodyDeclaration<?> declaration) {
        if (declaration instanceof MethodDeclaration method) {
            return method.getTypeAsString() + " " + method.getNameAsString()
                    + parameters(method.getParameters());
        }
        ConstructorDeclaration constructor = (ConstructorDeclaration) declaration;
        return constructor.getNameAsString() + parameters(constructor.getParameters());
    }

    private static String parameters(List<Parameter> parameters) {
        return parameters.stream().map(Parameter::toString)
                .collect(Collectors.joining(", ", "(", ")"));
    }

    private static BlockStmt bodyOf(BodyDeclaration<?> declaration) {
        if (declaration instanceof MethodDeclaration method) {
            return method.getBody().orElse(null);
        }
        if (declaration instanceof ConstructorDeclaration constructor) {
            return constructor.getBody();
        }
        return null;
    }

    private static CompilationUnit parse(String source) {
        try {
            return StaticJavaParser.parse(source);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Java source could not be parsed: " + e.getMessage());
        }
    }

    private static int offset(String source, Position position) {
        int line = 1;
        int index = 0;
        while (line < position.line && index < source.length()) {
            if (source.charAt(index) == '\n') {
                line++;
            }
            index++;
        }
        return Math.min(source.length(), index + Math.max(0, position.column - 1));
    }
}
