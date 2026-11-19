package com.richreach.codef.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.card.service.OwnedCardService;
import com.richreach.codef.client.CodefCardClient;
import com.richreach.codef.config.CodefProperties;
import com.richreach.codef.config.CodefSyncProperties;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.entity.CodefSyncJob;
import com.richreach.codef.repository.CodefSyncJobRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@SuppressWarnings("unchecked")
class CardSyncWorkerLockTest {
    @Test
    void accountIsLoadedOnlyAfterUserLock() {
        CodefSyncJobRepository jobs = mock(CodefSyncJobRepository.class);
        ConnectionUserLock userLock = mock(ConnectionUserLock.class);
        TransactionTemplate transaction = mock(TransactionTemplate.class);
        doAnswer(call -> {
            Consumer<TransactionStatus> callback = call.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transaction).executeWithoutResult(any());

        CodefSyncJob job = mock(CodefSyncJob.class);
        CodefOrgAccount account = mock(CodefOrgAccount.class);
        when(jobs.findUserIdById(91L)).thenReturn(Optional.of(7L));
        when(jobs.findWithAccountById(91L)).thenReturn(Optional.of(job));
        when(job.isInProgress()).thenReturn(true);
        when(job.getOrgAccount()).thenReturn(account);

        CardSyncWorker worker = new CardSyncWorker(jobs, userLock, mock(CodefCardClient.class),
            mock(OwnedCardService.class), mock(ApprovalCollector.class), transaction,
            new CodefSyncProperties(Duration.ofMinutes(5), Duration.ofHours(1)),
            mock(CodefProperties.class), Clock.systemUTC());
        worker.markRejected(91L);

        InOrder order = inOrder(jobs, userLock);
        order.verify(jobs).findUserIdById(91L);
        order.verify(userLock).lock(7L);
        order.verify(jobs).findWithAccountById(91L);
        verify(account).recordSyncError(any());
    }
}
