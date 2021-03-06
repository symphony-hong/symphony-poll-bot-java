package com.symphony.ps.pollbot.services;

import com.symphony.ps.pollbot.model.*;
import com.symphony.ps.pollbot.repository.PollRepository;
import com.symphony.ps.pollbot.repository.PollVoteRepository;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Slf4j
@Service
public class DataService {
    private final MongoTemplate mongoTemplate;
    private final PollRepository pollRepository;
    private final PollVoteRepository pollVoteRepository;

    public DataService(MongoTemplate mongoTemplate, PollRepository pollRepository, PollVoteRepository pollVoteRepository) {
        this.mongoTemplate = mongoTemplate;
        this.pollRepository = pollRepository;
        this.pollVoteRepository = pollVoteRepository;
    }

    boolean hasActivePoll(long userId) {
        return 1L == pollRepository.countByCreatorAndEnded(userId, null);
    }

    void createPoll(Poll poll) {
        pollRepository.save(poll);
        log.info("Poll added to database: {}", poll.toString());
    }

    void endPoll(long userId) {
        Poll poll = getActivePoll(userId);
        poll.setEnded(Instant.now());
        pollRepository.save(poll);
    }

    Poll getPoll(String id) {
        return pollRepository.findById(id).orElse(null);
    }

    Poll getActivePoll(long userId) {
        return pollRepository.findTopByCreatorAndEnded(userId, null);
    }

    private List<Poll> getHistoricalPolls(long userId, String streamId, int count) {
        PageRequest page = PageRequest.of(0, count);
        List<Poll> polls = (streamId != null) ?
            pollRepository.findAllByCreatorAndStreamIdAndEndedIsNotNullOrderByCreatedDesc(userId, streamId, page) :
            pollRepository.findAllByCreatorAndEndedIsNotNullOrderByCreatedDesc(userId, page);
        polls.sort(Comparator.comparing(Poll::getCreated));
        return polls;
    }

    private List<Poll> getActivePoll(long userId, String streamId) {
        Poll poll = (streamId != null) ?
            pollRepository.findTopByCreatorAndStreamIdAndEndedIsNullOrderByCreatedDesc(userId, streamId) :
            pollRepository.findTopByCreatorAndEndedIsNullOrderByCreatedDesc(userId);
        return poll == null ? new ArrayList<>() : Collections.singletonList(poll);
    }

    List<PollVote> getVotes(String pollId) {
        return pollVoteRepository.findAllByPollId(pollId);
    }

    List<PollResult> getPollResults(String pollId) {
        return mongoTemplate
            .aggregate(newAggregation(
                match(new Criteria("pollId").is(pollId)),
                group("answer").count().as("count"),
                project("count").and("answer").previousOperation(),
                sort(Sort.by(new Sort.Order(Sort.Direction.DESC, "count")))
            ), "pollVote", PollResult.class)
            .getMappedResults();
    }

    PollHistory getPollHistory(long userId, String streamId, String displayName, int count, boolean isActive) {
        List<Poll> polls = isActive ? getActivePoll(userId, streamId) : getHistoricalPolls(userId, streamId, count);

        if (polls.isEmpty()) {
            return null;
        }

        List<String> pollIds = polls.parallelStream()
            .map(Poll::getId)
            .collect(Collectors.toList());

        List<PollResult> results = mongoTemplate
            .aggregate(newAggregation(
                match(new Criteria("pollId").in(pollIds)),
                group("pollId", "answer")
                    .count().as("count")
                    .first("pollId").as("pollId")
                    .first("answer").as("answer"),
                project("pollId", "answer", "count"),
                sort(Sort.by(new Sort.Order(Sort.Direction.DESC, "count")))
            ), "pollVote", PollResult.class)
            .getMappedResults();

        List<PollHistoryItem> pollHistoryItems = new ArrayList<>();
        polls.forEach(poll -> {
            List<PollResult> thisPollResults = results.parallelStream()
                .filter(r -> r.getPollId().equals(poll.getId()))
                .collect(Collectors.toList());

            // Add in widths
            if (!thisPollResults.isEmpty()) {
                long maxVal = Collections.max(thisPollResults, Comparator.comparingLong(PollResult::getCount)).getCount();
                thisPollResults.forEach(r -> r.setWidth(Math.max(1, (int) (((float) r.getCount() / maxVal) * 200))));
            }

            // Add in 0 votes for options nobody voted on
            poll.getAnswers().stream()
                .map(PollResult::new)
                .filter(a -> !thisPollResults.contains(a))
                .forEach(thisPollResults::add);

            pollHistoryItems.add(PollHistoryItem.builder()
                .created(poll.getCreated())
                .ended(poll.getEnded())
                .questionText(poll.getQuestionText())
                .results(thisPollResults)
                .build()
            );
        });

        return PollHistory.builder()
            .room(streamId != null)
            .creatorDisplayName(displayName)
            .polls(pollHistoryItems)
            .build();
    }

    void createVote(PollVote vote) {
        pollVoteRepository.save(vote);
        log.info("Vote added to database: {}", vote.toString());
    }

    void createVotes(List<PollVote> votes) {
        pollVoteRepository.saveAll(votes);
        log.info("Rigged votes added to database");
    }

    boolean hasVoted(long userId, String pollId) {
        return pollVoteRepository.findTopByPollIdAndUserId(pollId, userId) != null;
    }

    void changeVote(long userId, String pollId, String answer) {
        PollVote vote = pollVoteRepository.findTopByPollIdAndUserId(pollId, userId);
        vote.setAnswer(answer);
        pollVoteRepository.save(vote);
    }
}
