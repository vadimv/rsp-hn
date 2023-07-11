package rsp.hn;

import rsp.App;
import rsp.jetty.JettyServer;
import rsp.routing.Routing;
import rsp.server.StaticResources;
import rsp.stateview.ComponentView;
import rsp.util.StreamUtils;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.lang.Float.parseFloat;
import static rsp.html.HtmlDsl.*;
import static rsp.routing.RoutingDsl.get;
import static rsp.util.ArrayUtils.concat;

/**
 *  A Hacker News API client implementation with 'infinite' scrolling
 */
public class JettyHn {

    public static void main(String[] args) throws Exception {
        final HnApiService hnApi = new HnApiService();
        final var bodyRef = createElementRef();
        final ComponentView<State> render = state -> newState ->
            html(head(link(attr("rel", "stylesheet"), attr("href","/res/style.css"))),
                    body(bodyRef,
                        div(attr("class", "header"),
                            h3(text("Hacker News"))),
                        div(attr("class", "content"),
                            of(StreamUtils.zipWithIndex(Arrays.stream(state.stories)).map(story ->
                                    div(
                                            span((story.getKey() + 1) + ". "),
                                            a(attr("href", story.getValue().url), text(story.getValue().name))
                                    ))
                              )),
                        window().on("scroll", c -> {
                            final var windowProps = c.props(window());
                            final var bodyProps = c.props(bodyRef);
                            windowProps.getString("innerHeight")
                                       .thenCompose(innerHeight -> windowProps.getString("pageYOffset")
                                       .thenCompose(pageYOffset -> bodyProps.getString("offsetHeight")
                                       .thenAccept(offsetHeight -> {
                                           if ((parseFloat(innerHeight) + parseFloat(pageYOffset)) >= parseFloat(offsetHeight)) {
                                               //final State currentState = state;
                                               final int newPageNum = state.pageNum + 1;
                                               final List<Integer> newStoriesIds = pageIds(Arrays.stream(state.storiesIds).boxed().collect(Collectors.toList()),
                                                                                           newPageNum,
                                                                                           HnApiService.PAGE_SIZE);
                                               final CompletableFuture<State> newStateCf = hnApi.stories(newStoriesIds)
                                                                                              .thenApply(newStories ->
                                                                                                      new State(state.storiesIds,
                                                                                                                concat(state.stories,
                                                                                                                       newStories.toArray(State.Story[]::new)),
                                                                                                                newPageNum));
                                               newState.applyWhenComplete(newStateCf);
                                           }
                                       })));
                        }).debounce(500)
                    )
                );

        final var route =  hnApi.storiesIds()
                .thenCompose(ids -> hnApi.stories(pageIds(ids, 0, HnApiService.PAGE_SIZE))
                        .thenApply(r -> new State(ids.stream().mapToInt(Integer::intValue).toArray(),
                                                    r.toArray(State.Story[]::new),
                                                    0)));

        final var server = new JettyServer<>(8080,"", new App<>(new Routing<>(get("", req -> route)),
                                                                             render),
                                                    new StaticResources(new File("src/main/java/rsp/examples/hn"),
                                                                   "/res/*"));
        server.start();
        server.join();
    }

    private static List<Integer> pageIds(List<Integer> storiesIds, int pageNum, int pageSize) {
        return storiesIds.subList(pageNum * pageSize, (pageNum + 1) * pageSize);
    }


}
