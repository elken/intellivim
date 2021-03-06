package org.intellivim.core.command.problems;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFixBase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Special QuickFixDescriptor with specific handling for imports
 * @author dhleong
 */
public class ImportsQuickFixDescriptor
        extends PromptingQuickFixDescriptor {
    ImportsQuickFixDescriptor(String problemDescription, String id,
                              String description,
                              int start, int end,
                              HighlightInfo.IntentionActionDescriptor descriptor) {
        super(problemDescription, id, description, start, end, descriptor, extractChoices(descriptor));
    }
    @Override
    protected Object invoke(final IntentionAction action, final Project project,
            final Editor editor, final PsiFile file, final String arg)
            throws QuickFixException {

        final List<PsiClass> toImport = getClassesToImport(action);
        if (toImport.size() > 1) {
            if (!StringUtil.isEmpty(arg)) {
                // they provided an arg; attempt to resolve
                final PsiReference ref = file.findReferenceAt(start);
                new AddImportAction(project, ref, editor, resolveArg(toImport, arg)).execute();
                return null;
            }

            // ambiguous!
            return extractChoices(descriptor);
        }

        // just a normal import
        return super.invoke(action, project, editor, file, arg);
    }

    private static PsiClass resolveArg(final List<PsiClass> classes,
            final String arg) throws QuickFixException {
        for (PsiClass klass : classes) {
            if (arg.equals(klass.getQualifiedName()))
                return klass;
        }

        throw new QuickFixException(arg + " is not a valid choice");
    }

    private static List<String> extractChoices(
            final HighlightInfo.IntentionActionDescriptor descriptor) {
        final List<PsiClass> classes = getClassesToImport(descriptor.getAction());
        if (classes.size() <= 1) {
            // no choices necessary
            return null;
        }

        final PsiClass[] sorted = attemptClassesSort(descriptor, classes);
        return ContainerUtil.map(sorted,
                new Function<PsiClass, String>() {
                    @Override
                    public String fun(final PsiClass psiClass) {
                        return psiClass.getQualifiedName();
                    }
                });
    }

    private static PsiClass[] attemptClassesSort(HighlightInfo.IntentionActionDescriptor descriptor, List<PsiClass> classes) {
        final PsiClass[] array = classes.toArray(new PsiClass[classes.size()]);
        final ImportClassFixBase<?, ?> action = (ImportClassFixBase<?, ?>) descriptor.getAction();
        try {
            final Field myRef = ImportClassFixBase.class.getDeclaredField("myRef");
            myRef.setAccessible(true);
            final PsiReference ref = (PsiReference) myRef.get(action);

            CodeInsightUtil.sortIdenticalShortNameClasses(array, ref);
        } catch (Exception e) {
            // oh well
            e.printStackTrace();
        }

        return array;
    }

    private static List<PsiClass> getClassesToImport(IntentionAction rawAction) {
        final ImportClassFixBase<?, ?> action = (ImportClassFixBase<?, ?>) rawAction;
        return action.getClassesToImport();
    }

    public static boolean handles(final HighlightInfo.IntentionActionDescriptor descriptor) {
        return descriptor.getAction() instanceof ImportClassFixBase;
    }
}
