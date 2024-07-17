package cn.dolphinstar.player.demo.other;

import java.util.UUID;

public class DramaInfo  extends  DramaBase{

    public  DramaInfo(String link , String text){
        this.ResLink = link;
        this.Title = text;
        UUID uuid = UUID.randomUUID();
        this.Id = uuid.toString();
    }

    public  String ResLink;
}

