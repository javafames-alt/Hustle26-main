package im.yaHustler.hud;

import java.util.ArrayList;
import java.util.List;
import im.yaHustler.font.FigmaFont;

/** Word wrapping with source offsets so karaoke highlighting survives line and page breaks. */
public record LyricLayout(List<Row> rows, float font, int characters) {
    public record Row(String text, int start) {
        public String sung(int characters) {
            int count=Math.clamp(characters-start,0,text.codePointCount(0,text.length()));
            return text.substring(0,text.offsetByCodePoints(0,count));
        }
    }

    public static LyricLayout measure(String raw, float width) {
        String text=raw.strip().replaceAll("\\s+"," ");
        List<Row> rows=List.of();
        float font=9;
        for(;font>=7.25f;font-=.25f) {
            rows=wrap(text,width,font);
            if(rows.size()<=2) break;
        }
        return new LyricLayout(List.copyOf(rows),Math.max(7.25f,font),text.codePointCount(0,text.length()));
    }

    public List<Row> page(float progress) {
        if(rows.isEmpty()) return List.of();
        int sung=Math.round(characters*Math.clamp(progress,0,1));
        int current=0;
        while(current+1<rows.size() && rows.get(current+1).start()<=sung) current++;
        int first=current/2*2;
        return rows.subList(first,Math.min(first+2,rows.size()));
    }

    private static List<Row> wrap(String text,float width,float font) {
        List<Row> result=new ArrayList<>();
        for(int start=0;start<text.length();) {
            int end=start,space=-1;
            while(end<text.length()) {
                int next=end+Character.charCount(text.codePointAt(end));
                if(FigmaFont.mediumWidth(text.substring(start,next),font/11)>width && end>start) break;
                if(Character.isWhitespace(text.codePointAt(end))) space=end;
                end=next;
            }
            if(end<text.length() && !Character.isWhitespace(text.codePointAt(end)) && space>start) end=space;
            result.add(new Row(text.substring(start,end).stripTrailing(),text.codePointCount(0,start)));
            start=end;
            while(start<text.length() && Character.isWhitespace(text.codePointAt(start))) start+=Character.charCount(text.codePointAt(start));
        }
        return result;
    }
}
