/**
 *    Copyright 2016 Sven Loesekann

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package ch.xxx.trader.usecase.services;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import ch.xxx.trader.domain.common.MongoUtils;
import ch.xxx.trader.domain.common.MongoUtils.TimeFrame;
import ch.xxx.trader.domain.model.entity.QuoteBf;
import ch.xxx.trader.usecase.common.DtoUtils;
import ch.xxx.trader.usecase.mappers.ReportMapper;
import ch.xxx.trader.usecase.services.ServiceUtils.MyTimeFrame;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Service
public class BitfinexService {
	private static final Logger LOG = LoggerFactory.getLogger(BitfinexService.class);
	public static final String BF_HOUR_COL = "quoteBfHour";
	public static final String BF_DAY_COL = "quoteBfDay";
	private final ReportGenerator reportGenerator;
	private final MyOrderBookClient orderBookClient;
	private final ReportMapper reportMapper;
	private final MyMongoRepository myMongoRepository;
	private final ServiceUtils serviceUtils;
	private final Scheduler mongoScheduler = Schedulers.newBoundedElastic(10, 10, "mongoImport", 10);
	private final Executor futureExecutor;

	public BitfinexService(ReportGenerator reportGenerator, ServiceUtils serviceUtils,
			@Qualifier("futureTaskExecutor") Executor futureExecutor, MyOrderBookClient orderBookClient,
			ReportMapper reportMapper, MyMongoRepository myMongoRepository) {
		this.reportGenerator = reportGenerator;
		this.orderBookClient = orderBookClient;
		this.reportMapper = reportMapper;
		this.myMongoRepository = myMongoRepository;
		this.serviceUtils = serviceUtils;
		this.futureExecutor = futureExecutor;
	}

	public Mono<String> getOrderbook(String currpair) {
		return this.orderBookClient.getOrderbookBitfinex(currpair);
	}

	public Mono<QuoteBf> insertQuote(Mono<QuoteBf> quote) {
		return this.myMongoRepository.insert(quote);
	}

	public Mono<QuoteBf> currentQuote(String pair) {
		Query query = MongoUtils.buildCurrentQuery(Optional.of(pair));
		return this.myMongoRepository.findOne(query, QuoteBf.class);
	}

	public Flux<QuoteBf> tfQuotes(String timeFrame, String pair) {
		Flux<QuoteBf> result = Flux.empty();
		if (MongoUtils.TimeFrame.TODAY.getValue().equals(timeFrame)) {
			Query query = MongoUtils.buildTodayQuery(Optional.of(pair));
			result = this.myMongoRepository.find(query, QuoteBf.class).filter(q -> filterEvenMinutes(q));
		} else if (MongoUtils.TimeFrame.SEVENDAYS.getValue().equals(timeFrame)) {
			Query query = MongoUtils.build7DayQuery(Optional.of(pair));
			result = this.myMongoRepository.find(query, QuoteBf.class, BF_HOUR_COL);
		} else if (MongoUtils.TimeFrame.THIRTYDAYS.getValue().equals(timeFrame)) {
			Query query = MongoUtils.build30DayQuery(Optional.of(pair));
			result = this.myMongoRepository.find(query, QuoteBf.class, BF_DAY_COL);
		} else if (MongoUtils.TimeFrame.NINTYDAYS.getValue().equals(timeFrame)) {
			Query query = MongoUtils.build90DayQuery(Optional.of(pair));
			result = this.myMongoRepository.find(query, QuoteBf.class, BF_DAY_COL);
		} else if (MongoUtils.TimeFrame.Month6.getValue().equals(timeFrame)) {
			Query query = MongoUtils.buildTimeFrameQuery(Optional.of(pair), TimeFrame.Month6);
			result = this.myMongoRepository.find(query, QuoteBf.class, BF_DAY_COL);
		} else if (MongoUtils.TimeFrame.Year1.getValue().equals(timeFrame)) {
			Query query = MongoUtils.buildTimeFrameQuery(Optional.of(pair), TimeFrame.Year1);
			result = this.myMongoRepository.find(query, QuoteBf.class, BF_DAY_COL);
		}

		return result;
	}

	public Mono<byte[]> pdfReport(String timeFrame, String pair) {
		Mono<byte[]> result = Mono.empty();
		if (MongoUtils.TimeFrame.TODAY.getValue().equals(timeFrame)) {
			Query query = MongoUtils.buildTodayQuery(Optional.of(pair));
			result = this.reportGenerator.generateReport(this.myMongoRepository.find(query, QuoteBf.class)
					.filter(this::filter10Minutes).map(this.reportMapper::convert));
		} else if (MongoUtils.TimeFrame.SEVENDAYS.getValue().equals(timeFrame)) {
			Query query = MongoUtils.build7DayQuery(Optional.of(pair));
			result = this.reportGenerator.generateReport(
					this.myMongoRepository.find(query, QuoteBf.class, BF_HOUR_COL).map(this.reportMapper::convert));
		} else if (MongoUtils.TimeFrame.THIRTYDAYS.getValue().equals(timeFrame)) {
			Query query = MongoUtils.build30DayQuery(Optional.of(pair));
			result = this.reportGenerator.generateReport(
					this.myMongoRepository.find(query, QuoteBf.class, BF_DAY_COL).map(this.reportMapper::convert));
		} else if (MongoUtils.TimeFrame.NINTYDAYS.getValue().equals(timeFrame)) {
			Query query = MongoUtils.build90DayQuery(Optional.of(pair));
			result = this.reportGenerator.generateReport(
					this.myMongoRepository.find(query, QuoteBf.class, BF_DAY_COL).map(this.reportMapper::convert));
		} else if (MongoUtils.TimeFrame.Month6.getValue().equals(timeFrame)) {
			Query query = MongoUtils.buildTimeFrameQuery(Optional.of(pair), TimeFrame.Month6);
			result = this.reportGenerator.generateReport(
					this.myMongoRepository.find(query, QuoteBf.class, BF_DAY_COL).map(this.reportMapper::convert));
		} else if (MongoUtils.TimeFrame.Year1.getValue().equals(timeFrame)) {
			Query query = MongoUtils.buildTimeFrameQuery(Optional.of(pair), TimeFrame.Year1);
			result = this.reportGenerator.generateReport(
					this.myMongoRepository.find(query, QuoteBf.class, BF_DAY_COL).map(this.reportMapper::convert));
		}
		return result;
	}

	public Mono<String> createBfAvg() {
		return this.myMongoRepository.ensureIndex(BF_HOUR_COL, DtoUtils.CREATEDAT).subscribeOn(this.mongoScheduler)
				.timeout(Duration.ofMinutes(5L))
				.doOnError(ex -> LOG.info("ensureIndex(" + BF_HOUR_COL + ") failed.", ex))
				.then(this.myMongoRepository.ensureIndex(BF_DAY_COL, DtoUtils.CREATEDAT)
						.subscribeOn(this.mongoScheduler).timeout(Duration.ofMinutes(5L))
						.doOnError(ex -> LOG.info("ensureIndex(" + BF_DAY_COL + ") failed.", ex)))
				.map(value -> this.createHourDayAvg()).timeout(Duration.ofHours(1L))
				.doOnError(ex -> LOG.info("createBfAvg() failed.", ex)).onErrorResume(e -> Mono.empty())
				.subscribeOn(this.mongoScheduler);
	}

	private String createHourDayAvg() {
		LOG.info("createHourDayAvg()");
		CompletableFuture<String> future3 = CompletableFuture.supplyAsync(() -> {
			this.createBfHourlyAvg();
			return "createBfHourlyAvg() Done.";
		}, CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS, this.futureExecutor));
		CompletableFuture<String> future4 = CompletableFuture.supplyAsync(() -> {
			this.createBfDailyAvg();
			return "createBfDailyAvg() Done.";
		}, CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS, this.futureExecutor));
		String combined = Stream.of(future3, future4).map(CompletableFuture::join).collect(Collectors.joining(" "));
		LOG.info(combined);
		return "done";
	}

	private void createBfHourlyAvg() {
		LocalDateTime startAll = LocalDateTime.now();
		MyTimeFrame timeFrame = this.serviceUtils.createTimeFrame(BF_HOUR_COL, QuoteBf.class, true);
		SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");
		Calendar now = Calendar.getInstance();
		now.setTime(Date.from(LocalDate.now().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()));
		while (timeFrame.end().before(now)) {
			Date start = new Date();
			Query query = new Query();
			query.addCriteria(
					Criteria.where(DtoUtils.CREATEDAT).gt(timeFrame.begin().getTime()).lt(timeFrame.end().getTime()));
			// Bitfinex
			Mono<Collection<QuoteBf>> collectBf = this.myMongoRepository.find(query, QuoteBf.class)
					.timeout(Duration.ofSeconds(5L)).doOnError(ex -> LOG.warn("Bitfinex prepare hour data failed", ex))
					.onErrorResume(ex -> Mono.empty()).subscribeOn(mongoScheduler)
					.collectMultimap(quote -> quote.getPair(), quote -> quote)
					.map(multimap -> multimap.keySet().stream()
							.map(key -> makeBfQuoteHour(key, multimap, timeFrame.begin(), timeFrame.end()))
							.collect(Collectors.toList()))
					.flatMap(myList -> Mono
							.just(myList.stream().flatMap(Collection::stream).collect(Collectors.toList())));
			collectBf.filter(myColl -> !myColl.isEmpty())
					.flatMap(myColl -> this.myMongoRepository.insertAll(Mono.just(myColl), BF_HOUR_COL)
							.timeout(Duration.ofSeconds(5L))
							.doOnError(ex -> LOG.warn("Bitfinex prepare hour data failed", ex))
							.onErrorResume(ex -> Mono.empty()).subscribeOn(mongoScheduler).collectList())
					.block();

			timeFrame.begin().add(Calendar.DAY_OF_YEAR, 1);
			timeFrame.end().add(Calendar.DAY_OF_YEAR, 1);
			LOG.info("Prepared Bitfinex Hour Data for: " + sdf.format(timeFrame.begin().getTime()) + " Time: "
					+ (new Date().getTime() - start.getTime()) + "ms");
		}
		LOG.info(this.serviceUtils.createAvgLogStatement(startAll, "Prepared Bitfinex Hourly Data Time:"));
	}

	private void createBfDailyAvg() {
		LocalDateTime startAll = LocalDateTime.now();
		MyTimeFrame timeFrame = this.serviceUtils.createTimeFrame(BF_DAY_COL, QuoteBf.class, false);
		SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");
		Calendar now = Calendar.getInstance();
		now.setTime(Date.from(LocalDate.now().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()));
		while (timeFrame.end().before(now)) {
			Date start = new Date();
			Query query = new Query();
			query.addCriteria(
					Criteria.where(DtoUtils.CREATEDAT).gt(timeFrame.begin().getTime()).lt(timeFrame.end().getTime()));
			// Bitfinex
			Mono<Collection<QuoteBf>> collectBf = this.myMongoRepository.find(query, QuoteBf.class)
					.timeout(Duration.ofSeconds(5L)).doOnError(ex -> LOG.warn("Bitfinex prepare day data failed", ex))
					.onErrorResume(ex -> Mono.empty()).subscribeOn(this.mongoScheduler)
					.collectMultimap(quote -> quote.getPair(), quote -> quote)
					.map(multimap -> multimap.keySet().stream()
							.map(key -> makeBfQuoteDay(key, multimap, timeFrame.begin(), timeFrame.end()))
							.collect(Collectors.toList()))
					.flatMap(myList -> Mono
							.just(myList.stream().flatMap(Collection::stream).collect(Collectors.toList())));
			collectBf.filter(myColl -> !myColl.isEmpty())
					.flatMap(myColl -> this.myMongoRepository.insertAll(Mono.just(myColl), BF_DAY_COL)
							.subscribeOn(mongoScheduler).timeout(Duration.ofSeconds(5L))
							.doOnError(ex -> LOG.warn("Bitfinex prepare day data failed", ex))
							.onErrorResume(ex -> Mono.empty()).subscribeOn(this.mongoScheduler).collectList())
					.subscribeOn(this.mongoScheduler).block();

			timeFrame.begin().add(Calendar.DAY_OF_YEAR, 1);
			timeFrame.end().add(Calendar.DAY_OF_YEAR, 1);
			LOG.info("Prepared Bitfinex Day Data for: " + sdf.format(timeFrame.begin().getTime()) + " Time: "
					+ (new Date().getTime() - start.getTime()) + "ms");
		}
		LOG.info(this.serviceUtils.createAvgLogStatement(startAll, "Prepared Bitfinex Daily Data Time:"));
	}

	private boolean filterEvenMinutes(QuoteBf quote) {
		return MongoUtils.filterEvenMinutes(quote.getCreatedAt());
	}

	private boolean filter10Minutes(QuoteBf quote) {
		return MongoUtils.filter10Minutes(quote.getCreatedAt());
	}

	private Collection<QuoteBf> makeBfQuoteHour(String key, Map<String, Collection<QuoteBf>> multimap, Calendar begin,
			Calendar end) {
		List<Calendar> hours = this.serviceUtils.createDayHours(begin);
		List<QuoteBf> hourQuotes = new LinkedList<QuoteBf>();
		for (int i = 0; i < 24; i++) {
			QuoteBf quoteBf = new QuoteBf(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
					BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "");
			quoteBf.setCreatedAt(hours.get(i).getTime());
			final int x = i;
			long count = multimap.get(key).stream().filter(quote -> {
				return quote.getCreatedAt().after(hours.get(x).getTime())
						&& quote.getCreatedAt().before(hours.get(x + 1).getTime());
			}).count();
			if (count > 2) {
				QuoteBf hourQuote = multimap.get(key).stream().filter(quote -> {
					return quote.getCreatedAt().after(hours.get(x).getTime())
							&& quote.getCreatedAt().before(hours.get(x + 1).getTime());
				}).reduce(quoteBf, (q1, q2) -> avgBfQuote(q1, q2, count));
				hourQuote.setPair(key);
				hourQuotes.add(hourQuote);
			}
		}
		return hourQuotes;
	}

	private Collection<QuoteBf> makeBfQuoteDay(String key, Map<String, Collection<QuoteBf>> multimap, Calendar begin,
			Calendar end) {
		List<QuoteBf> hourQuotes = new LinkedList<QuoteBf>();

		QuoteBf quoteBf = new QuoteBf(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
				BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "");
		quoteBf.setCreatedAt(begin.getTime());
		long count = multimap.get(key).stream().filter(quote -> {
			return quote.getCreatedAt().after(begin.getTime()) && quote.getCreatedAt().before(end.getTime());
		}).count();
		if (count > 2) {
			QuoteBf hourQuote = multimap.get(key).stream().filter(quote -> {
				return quote.getCreatedAt().after(begin.getTime()) && quote.getCreatedAt().before(end.getTime());
			}).reduce(quoteBf, (q1, q2) -> avgBfQuote(q1, q2, count));
			hourQuote.setPair(key);
			hourQuotes.add(hourQuote);
		}
		return hourQuotes;
	}

	private QuoteBf avgBfQuote(QuoteBf q1, QuoteBf q2, long count) {
		QuoteBf myQuote = new QuoteBf(this.serviceUtils.avgHourValue(q1.getMid(), q2.getMid(), count),
				this.serviceUtils.avgHourValue(q1.getBid(), q2.getBid(), count),
				this.serviceUtils.avgHourValue(q1.getAsk(), q2.getAsk(), count),
				this.serviceUtils.avgHourValue(q1.getLast_price(), q2.getLast_price(), count),
				this.serviceUtils.avgHourValue(q1.getLow(), q2.getLow(), count),
				this.serviceUtils.avgHourValue(q1.getHigh(), q2.getHigh(), count),
				this.serviceUtils.avgHourValue(q1.getVolume(), q2.getVolume(), count), q1.getTimestamp());
		myQuote.setCreatedAt(q1.getCreatedAt());
		return myQuote;
	}
}
