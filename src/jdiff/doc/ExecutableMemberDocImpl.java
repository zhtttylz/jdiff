package jdiff.doc;

import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;

abstract class ExecutableMemberDocImpl extends BaseProgramElementDoc {
    private final DocEnvironment environment;
    private final ExecutableElement element;

    private Parameter[] parameters;
    private Type[] thrownExceptions;

    ExecutableMemberDocImpl(DocEnvironment environment, ExecutableElement element) {
        super(environment, element);
        this.environment = environment;
        this.element = element;
    }

    ExecutableElement executableElement() {
        return element;
    }

    DocEnvironment environment() {
        return environment;
    }

    @Override
    public String name() {
        return element.getSimpleName().toString();
    }

    public Parameter[] parameters() {
        if (parameters == null) {
            List<Parameter> list = new ArrayList<>();
            for (VariableElement param : element.getParameters()) {
                list.add(new ParameterImpl(environment, param));
            }
            parameters = list.toArray(new Parameter[0]);
        }
        return parameters;
    }

    public Type[] thrownExceptions() {
        if (thrownExceptions == null) {
            thrownExceptions = environment.getTypes(element.getThrownTypes());
        }
        return thrownExceptions;
    }
}
