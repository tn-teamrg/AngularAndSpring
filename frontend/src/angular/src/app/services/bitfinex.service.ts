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
import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { QuoteBf } from '../common/quote-bf';
import { Utils } from './utils';
import { OrderbookBf } from '../common/orderbook-bf';

@Injectable({providedIn: 'root'})
export class BitfinexService {
  // eslint-disable-next-line @typescript-eslint/naming-convention
  BTCUSD = 'btcusd';
  // eslint-disable-next-line @typescript-eslint/naming-convention
  ETHUSD = 'ethusd';
  // eslint-disable-next-line @typescript-eslint/naming-convention
  LTCUSD = 'ltcusd';
  // eslint-disable-next-line @typescript-eslint/naming-convention
  XRPUSD = 'xrpusd';
  private reqOptionsArgs = { headers: new HttpHeaders().set( 'Content-Type', 'application/json' ) };
  private readonly bitfinex = '/bitfinex';

  private utils = new Utils();

  constructor(private http: HttpClient) {}

  getCurrentQuote(currencypair: string): Observable<QuoteBf> {
      return this.http.get<QuoteBf>(this.bitfinex+'/'+currencypair+'/current', this.reqOptionsArgs)
		.pipe(catchError(this.utils.handleError<QuoteBf>('getCurrentQuote')));
  }

  getTodayQuotes(currencypair: string): Observable<QuoteBf[]> {
      return this.http.get<QuoteBf[]>(this.bitfinex+'/'+currencypair+'/today', this.reqOptionsArgs)
		.pipe(catchError(this.utils.handleError<QuoteBf[]>('getTodayQuotes')));
  }

  get7DayQuotes(currencypair: string): Observable<QuoteBf[]> {
      return this.http.get<QuoteBf[]>(this.bitfinex+'/'+currencypair+'/7days', this.reqOptionsArgs)
		.pipe(catchError(this.utils.handleError<QuoteBf[]>('get7DayQuotes')));
  }

  get30DayQuotes(currencypair: string): Observable<QuoteBf[]> {
      return this.http.get<QuoteBf[]>(this.bitfinex+'/'+currencypair+'/30days', this.reqOptionsArgs)
		.pipe(catchError(this.utils.handleError<QuoteBf[]>('get30DayQuotes')));
  }

  get90DayQuotes(currencypair: string): Observable<QuoteBf[]> {
      return this.http.get<QuoteBf[]>(this.bitfinex+'/'+currencypair+'/90days', this.reqOptionsArgs)
		.pipe(catchError(this.utils.handleError<QuoteBf[]>('get90DayQuotes')));
  }

  get6MonthsQuotes(currencypair: string): Observable<QuoteBf[]> {
      return this.http.get<QuoteBf[]>(this.bitfinex+'/'+currencypair+'/6month', this.reqOptionsArgs)
		.pipe(catchError(this.utils.handleError<QuoteBf[]>('get6MonthQuotes')));
  }

  get1YearQuotes(currencypair: string): Observable<QuoteBf[]> {
      return this.http.get<QuoteBf[]>(this.bitfinex+'/'+currencypair+'/1year', this.reqOptionsArgs)
		.pipe(catchError(this.utils.handleError<QuoteBf[]>('get1YearQuotes')));
  }

  getOrderbook(currencypair: string): Observable<OrderbookBf> {
      const reqOptions = {headers: this.utils.createTokenHeader()};
      return this.http.get<OrderbookBf>(this.bitfinex+'/'+currencypair+'/orderbook', reqOptions)
		.pipe(catchError(this.utils.handleError<OrderbookBf>('getOrderbook')));
  }
}
