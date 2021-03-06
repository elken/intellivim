package org.intellivim.core.command.problems;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.intellivim.Command;
import org.intellivim.ProjectCommand;
import org.intellivim.Required;
import org.intellivim.Result;
import org.intellivim.SimpleResult;
import org.intellivim.inject.Inject;

/**
 * @author dhleong
 */
@Command("get_fixes")
public class GetFixesCommand extends ProjectCommand {

    @Required @Inject PsiFile file;
    @Required int offset;

    public GetFixesCommand(Project project, PsiFile file, int offset) {
        super(project);

        this.file = file;
        this.offset = offset;
    }

    @Override
    public Result execute() {
        final Problems problems = Problems.collectFrom(project, file);
        for (Problem p : problems) {
            if (p.containsOffset(offset)) {
                return SimpleResult.success(p.getFixes());
            }
        }

        // no exact match; try line-wise
        final Editor editor = createEditor(file, offset);
        final int line = editor.getCaretModel().getLogicalPosition().line + 1; // NB 0-based to 1-based line
        for (Problem p : problems) {
            if (p.isOnLine(line)) {
                return SimpleResult.success(p.getFixes());
            }
        }

        return SimpleResult.success();
    }
}
