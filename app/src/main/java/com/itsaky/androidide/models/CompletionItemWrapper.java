package com.itsaky.androidide.models;

import com.itsaky.androidide.app.StudioApp;
import com.itsaky.androidide.language.java.manager.JavaCharacter;
import com.itsaky.androidide.language.java.server.JavaLanguageServer;
import com.itsaky.lsp.CompletionItem;
import com.itsaky.lsp.CompletionItemKind;
import com.itsaky.lsp.Position;
import com.itsaky.lsp.Range;
import com.itsaky.lsp.TextDocumentIdentifier;
import com.itsaky.lsp.TextDocumentPositionParams;
import com.itsaky.lsp.TextEdit;
import io.github.rosemoe.editor.text.Content;
import io.github.rosemoe.editor.text.Cursor;
import io.github.rosemoe.editor.widget.CodeEditor;
import java.util.List;
import io.github.rosemoe.editor.text.CharPosition;
import com.itsaky.androidide.utils.Logger;

public class CompletionItemWrapper implements SuggestItem, Comparable {
    
    private CompletionItem item;
    private String prefix;

    public CompletionItemWrapper(CompletionItem item, String prefix) {
        this.item = item;
        this.prefix = prefix;
    }
    
    public String getInsertText() {
        return item.insertText == null ? item.label : item.insertText;
    }
    
    @Override
    public String getName() {
        return item.label;
    }

    @Override
    public String getDescription() {
        return item.detail;
    }

    @Override
    public String getReturnType() {
        return CompletionItemKind.asString(item.kind);
    }

    @Override
    public char getTypeHeader() {
        return getReturnType().charAt(0);
    }

    @Override
    public String getSortText() {
        return item.kind + getName();
    }

    @Override
    public void onSelectThis(CodeEditor editor) {
        try {
            final Cursor cursor = editor.getCursor();
            if(cursor.isSelected()) return;
            Range range = getIdentifierRange(cursor.getLeft(), editor.getText());
            String text = getInsertText();
            final boolean shiftLeft = text.contains("$0");
            final boolean atEnd = text.endsWith("$0");
            text = text.replace("$0", "");
            
            editor.getText().delete(range.start.line, range.start.column, range.end.line, range.end.column);
            cursor.onCommitText(text);
            
            if(shiftLeft && !atEnd) {
                editor.moveSelectionLeft();
            }
            
            if(item.command != null
                && item.command.command != null
                && item.command.command.equals(CompletionItem.COMMAND_TRIGGER_PARAMETER_HINTS)) {
                requestSignature(editor);
            }
             
            final List<TextEdit> edits = item.additionalTextEdits;
            if(edits != null && edits.size() > 0) {
                for(int i=0;i<edits.size();i++) {
                    final TextEdit edit = edits.get(i);
                    if(edit == null || edit.range == null || edit.newText == null) continue;
                    final Position start = edit.range.start;
                    final Position end =  edit.range.end;
                    if(start == null || end == null) continue;
                    if(start.equals(end)) {
                        editor.getText().insert(start.line, start.column, edit.newText);
                    } else {
                        editor.getText().replace(start.line, start.column, end.line, end.column, edit.newText);
                    }
                }
            }
        } catch (Throwable th) {}
    }
    
    private Range getIdentifierRange(int end, Content content) {
        int start = end;
        while(start > 0 && JavaCharacter.isJavaIdentifierPart(content.charAt(start - 1))) {
            start--;
        }
        
        CharPosition startPos = content.getIndexer().getCharPosition(start);
        CharPosition endPos = content.getIndexer().getCharPosition(end);
        return new Range(new Position(startPos.line, startPos.column), new Position(endPos.line, endPos.column));
    }
    
    private void requestSignature(CodeEditor editor) {
        final JavaLanguageServer server = StudioApp.getInstance().getJavaLanguageServer();
        if (server != null) {
            TextDocumentPositionParams p = new TextDocumentPositionParams();
            p.textDocument = new TextDocumentIdentifier(editor.getFile().toURI());
            p.position = new Position(editor.getCursor().getLeftLine(), editor.getCursor().getLeftColumn());
            server.signatureHelp(p, editor.getFile());
        }
    }

    @Override
    public int compareTo(Object p1) {
        if(p1 instanceof CompletionItemWrapper) {
   
            CompletionItemWrapper that = (CompletionItemWrapper) p1;
            return this.getSortText().compareTo(that.getSortText());
        }
        return 1;
    }
    
    public CompletionItem getItem() {
        return item;
    }
}
