// Copyright (C) 2014  Carl Pulley
// 
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// 
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <http://www.gnu.org/licenses/>.

package cakesolutions.example;

import akka.actor.ActorCell;
import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.stream.impl.RequestMore;
import cakesolutions.example.logging.Coloured;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class RequestAspect {

  @Pointcut(value = "execution (* akka.actor.ActorCell.sendMessage(..)) && args(message, sender)", argNames = "message,sender")
  public void sendMessagePointcut(Object message, Object sender) {}

  @Before(value = "sendMessagePointcut(message, sender)", argNames = "jp,message,sender")
  public void requestMore(JoinPoint jp, Object message, Object sender) {
    if (message instanceof RequestMore) {
      RequestMore request = (RequestMore)message;
      ActorSystem system = ((ActorCell)jp.getThis()).system();
      LoggingAdapter log = Logging.getLogger(system, jp.getThis().getClass());
      log.info("RequestMore("+request.subscription().impl().toString()+", "+request.demand()+")");
    }
  }

}
