/*
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
import { Component, OnInit, LOCALE_ID, Inject } from '@angular/core';
import {ActivatedRoute, Router } from '@angular/router';
import { trigger, state, animate, transition, style } from '@angular/animations';
import { BehaviorSubject, Observable } from 'rxjs';
import { BitstampService } from '../../services/bitstamp.service';
import { QuoteBs } from '../../common/quote-bs';
import { DetailBase, Tuple } from 'src/app/common/detail-base';

@Component({
  selector: 'app-bsdetail',
  templateUrl: './bsdetail.component.html',
  styleUrls: ['./bsdetail.component.scss'],
  animations: [
        trigger( 'showChart', [
            transition('false => true', [ style({ opacity: 0 }), animate(1000,  style({ opacity: 1 }))] )
    ])]
})
export class BsdetailComponent extends DetailBase implements OnInit {
    public currQuote: QuoteBs;
    protected chartShow =  new BehaviorSubject(false);
    protected todayQuotes: QuoteBs[] = [];

    constructor(private route: ActivatedRoute, private router: Router, private serviceBs: BitstampService,
	@Inject(LOCALE_ID) private myLocale: string) {
		super(myLocale);
	}

    ngOnInit() {
	    this.chartShow.next(false);
        this.route.params.subscribe(params => {
        this.serviceBs.getCurrentQuote(params.currpair)
        .subscribe(quote => {
            this.currQuote = quote;
            this.currPair = this.utils.getCurrpairName(this.currQuote.pair);});
        this.serviceBs.getTodayQuotes(this.route.snapshot.paramMap.get('currpair'))
        .subscribe(quotes => {
            this.todayQuotes = quotes;
			this.updateChartData(quotes.map(quote => new Tuple<string, number>(quote.createdAt, quote.last)));
			this.chartShow.next(true);
            });
        });
    }

  back(): void {
	this.router.navigate(['/']);
  }

    changeTf() {
	    this.chartShow.next(false);
        const currpair = this.route.snapshot.paramMap.get('currpair');
        let quoteObserv: Observable<QuoteBs[]>;
            if(this.timeframe === this.utils.timeframes[1]) {
				quoteObserv = this.serviceBs.get7DayQuotes(currpair);
			} else if(this.timeframe === this.utils.timeframes[2]) {
				quoteObserv = this.serviceBs.get30DayQuotes(currpair);
			} else if(this.timeframe === this.utils.timeframes[3]) {
				quoteObserv = this.serviceBs.get90DayQuotes(currpair);
			} else if ( this.timeframe === this.utils.timeframes[4] ) {
			    quoteObserv = this.serviceBs.get6MonthsQuotes( currpair );
		    } else if ( this.timeframe === this.utils.timeframes[5] ) {
			    quoteObserv = this.serviceBs.get1YearQuotes( currpair );
		    } else {
				quoteObserv = this.serviceBs.getTodayQuotes(currpair);
			}

        quoteObserv.subscribe(quotes => {
            this.todayQuotes = quotes;
			this.updateChartData(quotes.map(quote => new Tuple<string, number>(quote.createdAt, quote.last)));
			this.chartShow.next(true);
            });
    }

    showReport() {
        const currpair = this.route.snapshot.paramMap.get('currpair');
        const url = '/bitstamp' + this.utils.createReportUrl(this.timeframe, currpair);
        window.open(url);
    }

}
