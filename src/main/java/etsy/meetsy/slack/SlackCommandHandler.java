package etsy.meetsy.slack;

import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.context.builtin.SlashCommandContext;
import com.slack.api.bolt.context.builtin.ViewSubmissionContext;
import com.slack.api.bolt.jetty.SlackAppServer;
import com.slack.api.bolt.request.builtin.SlashCommandRequest;
import com.slack.api.bolt.request.builtin.ViewSubmissionRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.users.profile.UsersProfileGetRequest;
import com.slack.api.methods.request.views.ViewsOpenRequest;
import com.slack.api.methods.response.users.profile.UsersProfileGetResponse;
import com.slack.api.methods.response.views.ViewsOpenResponse;
import com.slack.api.model.view.ViewState;
import com.slack.api.socket_mode.SocketModeClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class SlackCommandHandler {
    private static final String SLACK_BOT_TOKEN = System.getenv("SLACK_BOT_TOKEN");
    private static final String SLACK_APP_TOKEN = System.getenv("SLACK_APP_TOKEN");

    private static App MEETSY_APP;

    public static void startListening() throws Exception {
        handleCommands();
        SocketModeApp socketModeApp = new SocketModeApp(SLACK_APP_TOKEN, SocketModeClient.Backend.JavaWebSocket, MEETSY_APP);
        socketModeApp.start();

        // SocketModeApp expects an env variable: SLACK_APP_TOKEN
        new SocketModeApp(MEETSY_APP).start();

        SlackAppServer server = new SlackAppServer(MEETSY_APP);
        server.start();
    }

    private static void handleCommands() {
        MEETSY_APP = new App(AppConfig.builder().singleTeamBotToken(SLACK_BOT_TOKEN).build());

        MEETSY_APP.command("/meetsycreate", SlackCommandHandler::handleMeetsyCreate);
        MEETSY_APP.command("/meetsy-enroll", SlackCommandHandler::handleMeetsyEnroll);

        MEETSY_APP.blockAction("create-duration-selection-action", (req, ctx) -> ctx.ack());
        MEETSY_APP.blockAction("create-frequency-selection-action", (req, ctx) -> ctx.ack());
        MEETSY_APP.blockAction("create-joinType-selection-action", (req, ctx) -> ctx.ack());
        MEETSY_APP.viewSubmission("", (SlackCommandHandler::handleCreateMeetsyViewSubmission)); //todo: why is callbackID blank?
    }

    private static Response handleMeetsyCreate(SlashCommandRequest req, SlashCommandContext ctx) throws SlackApiException, IOException {
        ViewsOpenResponse viewsOpenRes = ctx.client().viewsOpen(ViewsOpenRequest.builder()
                .viewAsString(FileUtils.readFileToString(new File("modals/CreateMeetsyModal.json"), StandardCharsets.UTF_8))
                .triggerId(ctx.getTriggerId()).build());
        if (viewsOpenRes.isOk()) {
            return ctx.ack();
        }
        return ctx.ack("Welcome!  Meetsy is now setup for this channel\n>*Duration: 30 min*    :alarm_clock:\n>*Frequency: monthly*    :spiral_calendar_pad:\n>*Invites: member opt in*    :white_check_mark:");
    }

    private static Response handleMeetsyEnroll(SlashCommandRequest req, SlashCommandContext ctx) throws SlackApiException, IOException {
        log.debug("Channel: " + req.getPayload().getChannelName());
        log.debug("User: " + req.getPayload().getUserName());

        boolean successfullyEnrolled = enrollInMeetsy(MEETSY_APP.getClient(), req.getPayload().getChannelId(), req.getPayload().getUserId());

        if (successfullyEnrolled) {
            return ctx.ack(":wave: You are enrolled in meetsy!");
        }
        else {
            return ctx.ack("There was an error enrolling into meetsy.  Please try again or contact helpdesk");
        }
    }

    private static boolean enrollInMeetsy(MethodsClient slackMethodsClient, String channel, String userId) throws SlackApiException, IOException {
        UsersProfileGetResponse usersProfileGetResponse = slackMethodsClient.usersProfileGet(UsersProfileGetRequest.builder().token(SLACK_BOT_TOKEN).user(userId).build());
        //todo: store in database
        return true;
    }

    private static Response handleCreateMeetsyViewSubmission(ViewSubmissionRequest req, ViewSubmissionContext ctx) {
        String privateMetadata = req.getPayload().getView().getPrivateMetadata();
        Map<String, Map<String, ViewState.Value>> stateValues = req.getPayload().getView().getState().getValues();
        ViewState.SelectedOption duration = stateValues.get("duration-block").get("create-duration-selection-action").getSelectedOption();
        ViewState.SelectedOption frequency = stateValues.get("frequency-block").get("create-frequency-selection-action").getSelectedOption();
        ViewState.SelectedOption join = stateValues.get("join-block").get("create-joinType-selection-action").getSelectedOption();
        Map<String, String> errors = new HashMap<>();
        if (duration == null) {
            errors.put("duration-block", "Duration is required");
        }
        if (frequency == null) {
            errors.put("frequency-block", "Frequency is required");
        }
        if (join == null) {
            errors.put("join-block", "Join is required");
        }
        if (!errors.isEmpty()) {
            return ctx.ack(r -> r.responseAction("errors").errors(errors));
        } else {
            // TODO: may store the stateValues and privateMetadata
            // Responding with an empty body means closing the modal now.
            // If your app has next steps, respond with other response_action and a modal view.
            return ctx.ack(); //todo: figure out how to show a reply.
        }
    }
}
