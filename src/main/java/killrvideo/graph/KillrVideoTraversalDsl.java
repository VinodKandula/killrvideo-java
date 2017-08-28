package killrvideo.graph;

import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.Scope;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.GremlinDsl;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static killrvideo.graph.KillrVideoTraversalConstants.*;
import static org.apache.tinkerpop.gremlin.process.traversal.Operator.assign;
import static org.apache.tinkerpop.gremlin.process.traversal.Order.decr;
import static org.apache.tinkerpop.gremlin.process.traversal.P.*;
import static org.apache.tinkerpop.gremlin.structure.Column.keys;
import static org.apache.tinkerpop.gremlin.structure.Column.values;

/**
 * Define our KillrVideo DSL (Domain Specific Language)
 *
 * Initial DSL credit goes to Stephen Mallette who provided me with a working
 * KillrVideo DSL example that I used as the basis for the following code.
 * Be sure to check out his excellent blog post explaining DSL's
 * here -> https://www.datastax.com/dev/blog/gremlin-dsls-in-java-with-dse-graph
 */
@GremlinDsl(traversalSource = "killrvideo.graph.KillrVideoTraversalSourceDsl")
public interface KillrVideoTraversalDsl<S, E> extends GraphTraversal.Admin<S, E> {

    /**
     * Traverses from a "video" to a "rated" edge.
     */
    public default GraphTraversal<S, Edge> ratings() {
        return inE(EDGE_RATED);
    }

    /**
     * Calls {@link #rated(int, int)} with both arguments as zero.
     */
    public default GraphTraversal<S, Vertex> rated() {
        return rated(0,0);
    }

    /**
     * Traverses from a "user" to a "video" over the "rated" edge, filtering those edges as specified. If both arguments
     * are zero then there is no rating filter.
     *
     * @param min minimum rating to consider
     * @param max maximum rating to consider
     */
    public default GraphTraversal<S, Vertex> rated(int min, int max) {
        if (min < 0 || max > 5) throw new IllegalArgumentException("min must be a value between 0 and 5");
        if (max < 0 || max > 5) throw new IllegalArgumentException("min must be a value between 0 and 5");
        if (min != 0 && max != 0 && min > max) throw new IllegalArgumentException("min cannot be greater than max");

        if (min == 0 && max == 0)
            return out(EDGE_RATED);
        else if (min == 0)
            return outE(EDGE_RATED).has(KEY_RATING, gt(min)).inV();
        else if (max == 0)
            return outE(EDGE_RATED).has(KEY_RATING, lt(min)).inV();
        else
            return outE(EDGE_RATED).has(KEY_RATING, P.between(min, max)).inV();
    }

    /**
     * Creates a "rated" edge with "rating" property from a "user" to a "video"
     * @param userId
     * @param rating
     * @return
     */
    public default GraphTraversal<S, Vertex> rated(UUID userId, Integer rating) {
        if (null == userId)
            throw new IllegalArgumentException("The userId must not be null");
        if (rating < 1 || rating > 5)
            throw new IllegalArgumentException("rating must be a value between 1 and 5");

        /**
         * As mentioned in the javadocs this step assumes an incoming "video" vertex. it is immediately labelled as
         * "^video". the addition of the caret prefix has no meaning except to provide for a unique labelling space
         * within the DSL itself.
         *
         * Also note there is no check to see if the rating already exists for the video, user, and rating passed in.
         * This is because ratings have multiple cardinality and we can have as many ratings from a user for a video
         * as we want, no check needed.
         */
        return ((KillrVideoTraversal) as("^video")).
                coalesce(__.user(userId)
                        .addE(EDGE_RATED).property(KEY_RATING, rating).to("^video").inV()
                );
    }

    /**
     * Calls {@link #rated(int, int)} with both arguments as zero.
     */
    public default GraphTraversal<S, Vertex> watched() {
        return out(EDGE_RATED);
    }

    /**
     * Traverses from a "video" to a "user" over the "uploaded" edge.
     */
    public default GraphTraversal<S, Vertex> uploaders() {
        return in(EDGE_UPLOADED).hasLabel(VERTEX_USER);
    }

    /**
     * Provides a traversal that filters on one user without attempting
     * to create the user record on a failure to find it
     * @param userId
     * @return
     */
    public default GraphTraversal<S, Vertex> user(UUID userId) {
        return (KillrVideoTraversal) __.V().has(VERTEX_USER, KEY_USER_ID, userId);
    }

    /**
     * Gets or creates a "user".
     * <p/>
     * This step first checks for existence of a user given their userId. If it exists then the user is
     * returned.  If not, the user is created.  This does not allow for updates, only an initial insert.
     * The app itself does not allow for updates to existing user records so I kept to the same design here.
     * @param userId
     * @param email
     * @param added_date
     * @return
     */
    public default GraphTraversal<S, Vertex> user(UUID userId, String email, Date added_date) {
        if (null == userId)
            throw new IllegalArgumentException("The userId must not be null");
        if (null == email || email.isEmpty())
            throw new IllegalArgumentException("The email of the user must not be null or empty");
        if (null == added_date)
            throw new IllegalArgumentException("The added_date must not be null");

        return coalesce(
                __.V().has(VERTEX_USER, KEY_USER_ID, userId),
                __.addV(VERTEX_USER)
                    .property(KEY_USER_ID, userId)
                    .property(KEY_ADDED_DATE, added_date)
                    .property(KEY_EMAIL, email));
    }

    /**
     * Creates an "uploaded" edge from a "user" to a "video"
     * @param userId
     * @return
     */
    public default GraphTraversal<S, Vertex> uploaded(UUID userId) {
        if (null == userId)
            throw new IllegalArgumentException("The userId must not be null");
        /**
         * As mentioned in the javadocs this step assumes an incoming "video" vertex. it is immediately labelled as
         * "^video". the addition of the caret prefix has no meaning except to provide for a unique labelling space
         * within the DSL itself.
         */
        return ((KillrVideoTraversal) as("^video")).
                coalesce(
                        __.uploaders().has(KEY_USER_ID, userId),
                        __.V().user(userId)
                                .addE(EDGE_UPLOADED).to("^video").inV()
                );
    }

    /**
     * Traverses from a "video" to a "tag" over the "taggedWith" edge.
     */
    public default GraphTraversal<S, Vertex> taggers() {
        return out(EDGE_TAGGED_WITH).hasLabel(VERTEX_TAG);
    }

    /**
     * Checks for the existence of a tag and returns it if it exists.  If not,
     * it creates a new tag vertex.
     * @param name
     * @param tagged_date
     * @return
     */
    public default GraphTraversal<S, Vertex> tag(String name, Date tagged_date) {
        if (null == name || name.isEmpty())
            throw new IllegalArgumentException("The name of the tag must not be null or empty");
        if (null == tagged_date)
            throw new IllegalArgumentException("The tagged_date must not be null");

        return coalesce(
                __.V().has(VERTEX_TAG, KEY_NAME, name),
                __.addV(VERTEX_TAG)
                        .property(KEY_NAME, name)
                        .property(KEY_TAGGED_DATE, tagged_date));
    }

    /**
     * Creates a "taggedWith" edge from a "video" to a "tag"
     * @param name
     * @param tagged_date
     * @return
     */
    public default GraphTraversal<S, Vertex> taggedWith(String name, Date tagged_date) {
        /**
         * no validation here as it would just duplicate what is happening in tag(). note the use of the
         * cast to KillrVideoTraversal. in this case, we want to use a DSL step within the DSL itself, but we want to
         * start the traversal with a GraphTraversal step which thus returns a GraphTraversal. The only ways to get
         * around this is to do the cast or to create a version of the GraphTraversal step in the DSL that will
         * provide such access and return a "KillrVideoTraversal".

         * as mentioned in the javadocs this step assumes an incoming "video" vertex. it is immediately labelled as
         * "^video". the addition of the caret prefix has no meaning except to provide for a unique labelling space
         * within the DSL itself.
         */
        return ((KillrVideoTraversal) as("^video")).
                coalesce(__.taggers().has(KEY_NAME, name),
                         __.tag(name, tagged_date)
                                 .addE(EDGE_TAGGED_WITH).from("^video").inV());
    }

    /**
     * This step is an alias for the {@code sideEffect()} step. As an alias, it makes certain aspects of the DSL more
     * readable.
     */
    public default GraphTraversal<S,?> ensure(Traversal<?,?> mutationTraversal) {
        return sideEffect(mutationTraversal);
    }

    /**
     * Recommendation engine - User rating engine
     * Using the videos I really like (rating 4-5), find other users who also really like the same videos, and grab
     * videos they really like while excluding any videos I have watched.
     *
     * @param recommendations the number of recommended movies to return
     * @param minRating the minimum rating to allow for
     * @param numRatingsToSample the number of global user ratings to sample (smaller means faster traversal)
     * @param localUserRatingsToSample the number of local user ratings to limit by
     *
     * A big thank you to Bob Briody, Sandeep Tamhankar, and the rest of the recommendation
     * engine hackathon team for coming up with the following traversal and passing on some
     * working code for me to start with.
     */
    public default GraphTraversal<S, Map<String, Object>> recommendHackathon(
            int recommendations, int minRating, int numRatingsToSample, int localUserRatingsToSample)
    {
        if (recommendations <= 0) throw new IllegalArgumentException("recommendations must be greater than zero");
        if (minRating <= 0) throw new IllegalArgumentException("minRating must be greater than zero");
        if (numRatingsToSample <= 0) throw new IllegalArgumentException("numRatingsToSample must be greater than zero");
        if (localUserRatingsToSample <= 0) throw new IllegalArgumentException("localUserRatingsToSample must be greater than zero");

        /**
         * Notice that I call killr.users() using our DSL and then ".as()" to set the result as "currentUser".
         * This comes into play within the traversal right below it as a way to keep the whole
         * traversal a "one-liner" that prevents us from having to store multiple traversals in separate
         * variables or something along those lines.
         */
        return
                // Start with the current user and store for later
                as("^currentUser")
                        // using watched() from our DSL get all of the videos the user watched and store them
                        .map(__.watched().dedup().fold()).as("^watchedVideos")
                        // go back to our current user
                        .select("^currentUser")
                        // for the video's I rated highly...
                        .outE(EDGE_RATED).has(KEY_RATING, gte(minRating)).inV()
                        // what other users rated those videos highly? (this is like saying "what users share my taste")
                        .inE(EDGE_RATED).has(KEY_RATING, gte(minRating))
                        // but don't grab too many, or this won't work OLTP, and "by('rating')" favors the higher ratings
                        .sample(numRatingsToSample).by(KEY_RATING).outV()
                        // (except me of course)
                        .where(neq("^currentUser"))
                        // Now we're working with "similar users". For those users who share my taste, grab N highly rated videos.
                        // Save the rating so we can sum the scores later, and use sack() because it does not require path information. (as()/select() was slow)
                        .local(__.outE(EDGE_RATED).has(KEY_RATING, gte(minRating)).limit(localUserRatingsToSample)).sack(assign).by(KEY_RATING).inV()
                        // excluding the videos I have already watched
                        .not(__.where(within("^watchedVideos")))
                        // what are the most popular videos as calculated by the sum of all their ratings
                        .group().by().by(__.sack().sum())
                        // now that we have that big map of [video: score], lets order it
                        .order(Scope.local).by(values, decr).limit(Scope.local, recommendations).select(keys).unfold()
                        // Ok, we have our video vertices, now lets tag on the user vertex of the user who uploaded each video
                        .project(VERTEX_VIDEO, VERTEX_USER)
                        .by()
                        .by(__.in(EDGE_UPLOADED));
    }
}