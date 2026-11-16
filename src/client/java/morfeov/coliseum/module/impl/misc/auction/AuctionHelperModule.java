package morfeov.coliseum.module.impl.misc.auction;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
public final class AuctionHelperModule {
    private static final AuctionHelperModule instance=new AuctionHelperModule();
    private static final Pattern price=Pattern.compile("(?iu)(?:цена|стоимость|price|\\$)\\s*[:：]?\\s*([0-9][0-9 ,\\u00a0\\u2009]*)");
    public static AuctionHelperModule getInstance(){return instance;}
    public String formatText2(String text){return text.replaceAll("(?i)§[0-9a-fk-or]","").toLowerCase(Locale.ROOT).replace('o','о').replace('e','е').replace('a','а').replace('c','с');}
    public boolean matchesCriteria3(String title){String text=formatText2(title);return title.contains("1A0ꀄ")||title.contains("ꈁꀀꈂꌲꈂꀁ§0ꈃꄙ")||text.contains("漢:")||text.contains("аукцион")||text.contains("поиск:")||text.contains("п:")||text.contains("категория:")||title.toLowerCase(Locale.ROOT).contains("auction")||text.contains("найденные товары");}
    public int computeInt(ItemStack item){
        var lore=item.get(DataComponents.LORE);if(lore==null)return -1;
        for(var line:lore.lines()){
            String text=line.getString().replaceAll("(?i)§[0-9a-fk-or]","");var match=price.matcher(text);
            String digits=match.find()?match.group(1).replaceAll("[^0-9]",""):(formatText2(text).contains("цена")||formatText2(text).contains("монет"))?text.replaceAll("[^0-9]",""):"";
            if(digits.isEmpty())continue;try{long amount=Long.parseLong(digits);if(amount>0&&amount<=Integer.MAX_VALUE)return(int)amount;}catch(NumberFormatException ignored){}
        }
        return -1;
    }
}
