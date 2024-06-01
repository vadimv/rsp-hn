package rsp.hn;

import rsp.App;
import rsp.component.ComponentView;
import rsp.jetty.WebServer;
import rsp.server.StaticResources;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.lang.Float.parseFloat;
import static rsp.html.HtmlDsl.*;
import static rsp.util.ArrayUtils.concat;

/**
 *  A Hacker News API client implementation with 'infinite' scrolling
 */
public class JettyHn {

    public static void main(String[] args) throws Exception {
        final HnApiService hnApi = new HnApiService();
        final var bodyRef = createElementRef();
        final var window = window();
        final ComponentView<State> render = state -> newState ->
            html(head(link(attr("rel", "stylesheet"), attr("href","/res/style.css"))),
                    body(elementId(bodyRef),
                        div(attr("class", "header"),
                            h3(text("Hacker News"))),
                        div(attr("class", "content"),
                            of(StreamUtils.zipWithIndex(Arrays.stream(state.stories)).map(story ->
                                    div(
                                            span((story.getKey() + 1) + ". "),
                                            a(attr("href", story.getValue().url), text(story.getValue().name))
                                    ))
                              )),
                        window.on("scroll", c -> {
                            final var windowProps = c.propertiesByRef(window.ref());
                            final var bodyProps = c.propertiesByRef(bodyRef);
                            windowProps.getString("innerHeight")
                                       .thenCompose(innerHeight -> windowProps.getString("pageYOffset")
                                       .thenCompose(pageYOffset -> bodyProps.getString("offsetHeight")
                                       .thenAccept(offsetHeight -> {
                                           if ((parseFloat(innerHeight) + parseFloat(pageYOffset)) >= parseFloat(offsetHeight)) {
                                               final int newPageNum = state.pageNum + 1;
                                               final List<Integer> newStoriesIds = pageIds(Arrays.stream(state.storiesIds).boxed().toList(),
                                                                                           newPageNum,
                                                                                           HnApiService.PAGE_SIZE);
                                               final CompletableFuture<State> newStateCf = hnApi.stories(newStoriesIds)
                                                                                              .thenApply(newStories ->
                                                                                                      new State(state.storiesIds,
                                                                                                                concat(state.stories,
                                                                                                                       newStories.toArray(State.Story[]::new)),
                                                                                                                newPageNum));
                                               newState.setStateWhenComplete(newStateCf);
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

        final var server = new WebServer(8080, new App<>(route,
                                                              render),
                                                    new StaticResources(new File("src/main/java/rsp/hn"),
                                                                   "/res/*"));
        server.start();
        server.join();
    }

    private static List<Integer> pageIds(List<Integer> storiesIds, int pageNum, int pageSize) {
        return storiesIds.subList(pageNum * pageSize, (pageNum + 1) * pageSize);
    }


}
