package org.intellivim.core.command.complete;

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.OffsetMap;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.DocumentUtil;
import org.intellivim.core.util.FileUtil;
import org.intellivim.core.util.IntelliVimUtil;

import java.io.IOException;
import java.lang.reflect.Constructor;

/**
 * @author dhleong
 */
public class CompletionParametersUtil {

    static Constructor<CompletionParameters> sConstructor;

    static CompletionParameters newInstance(PsiElement position, PsiFile originalFile,
            CompletionType completionType, int offset, int invocationCount, Editor editor) {

        try {

            final Constructor<CompletionParameters> cached = sConstructor;

            final Constructor<CompletionParameters> ctor;
            if (cached == null) {
                ctor = CompletionParameters.class.getDeclaredConstructor(
                        PsiElement.class /* position */, PsiFile.class /* originalFile */,
                        CompletionType.class, int.class /* offset */, int.class /* invocationCount */,
                        Editor.class
                );
                ctor.setAccessible(true);
                sConstructor = ctor;
            } else {
                ctor = cached;
            }

            return ctor.newInstance(position, originalFile, completionType, offset, invocationCount, editor);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    static CompletionParameters from(Editor editor, PsiFile psiFile, int offset) {

        final PsiElement position = psiFile.findElementAt(offset);
        final CompletionType completionType = CompletionType.BASIC;

        if (position == null) {
            System.out.println("Couldn't find element at " + offset);
            System.out.println("psif=" + psiFile.getText());
            try {
                System.out.println("file=" + new String(psiFile.getVirtualFile().contentsToByteArray()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

//        final Lookup lookup = new VimLookup(project, editor);

        final int invocationCount = 0;

        final OffsetMap offsetMap = new OffsetMap(editor.getDocument());
        final CompletionContext context = new CompletionContext(psiFile, offsetMap);
        position.putUserData(CompletionContext.COMPLETION_CONTEXT_KEY, context);

        // we need to insert a dummy identifier so there's something there.
        //  this is what intellij does typically
        final PsiElement completionPosition = insertDummyIdentifier(
                psiFile, position);

        return newInstance(completionPosition, psiFile,
                completionType, offset, invocationCount, editor);
    }

    /** based on CodeCompletionHandlerBase */
    private static PsiElement insertDummyIdentifier(final PsiFile originalFile,
            final PsiElement position) {
        final InjectedLanguageManager manager = InjectedLanguageManager
                .getInstance(originalFile.getProject());
        final PsiFile hostFile = manager.getTopLevelFile(originalFile);

        final PsiFile[] hostCopy = {null};
        DocumentUtil.writeInRunUndoTransparentAction(new Runnable() {
            @Override
            public void run() {
                final int start = position.getTextOffset();
                final int end = start + position.getTextLength();
                hostCopy[0] = FileUtil.createFileCopy(hostFile, start, end);
            }
        });

        final Document copyDocument = hostCopy[0].getViewProvider().getDocument();
        if (copyDocument == null) {
            throw new IllegalStateException("No document found for copy");
        }

        IntelliVimUtil.runWriteCommand(new Runnable() {
            @Override
            public void run() {
                final String dummyIdentifier =
                        CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED;
                if (StringUtil.isEmpty(dummyIdentifier)) return;

                final int startOffset = position.getTextOffset();
                final int endOffset = startOffset + position.getTextLength();
//                        int startOffset = hostMap.getOffset(CompletionInitializationContext.START_OFFSET);
//                        int endOffset = hostMap.getOffset(CompletionInitializationContext.SELECTION_END_OFFSET);
                copyDocument.replaceString(startOffset, endOffset, dummyIdentifier);
            }
        });

        PsiDocumentManager.getInstance(originalFile.getProject())
                .commitDocument(copyDocument);
//        return hostCopy[0];
        return hostCopy[0].findElementAt(position.getTextOffset());
    }

}
