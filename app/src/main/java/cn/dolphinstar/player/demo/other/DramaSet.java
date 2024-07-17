package cn.dolphinstar.player.demo.other;

import java.util.List;
import java.util.UUID;

public class DramaSet  extends  DramaBase{

    public  DramaSet(String t , List<DramaInfo> d){
        this.Title = t;
        this.Data = d;
        UUID uuid = UUID.randomUUID();
        this.Id = uuid.toString();
    }

    public  List<DramaInfo> Data;
}
