package etsy.meetsy;


import etsy.meetsy.slack.SlackCommandHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class MeetsyBoltPOC {

    public static void main(String[] args) throws Exception {
        SlackCommandHandler.startListening();
    }


}