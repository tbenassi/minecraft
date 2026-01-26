package github.tbenassi.devauth.mixin;

import net.minecraft.client.session.report.AbuseReportContext;
import net.minecraft.client.session.report.ReporterEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Provides the means to access protected members of the Abuse Report Context.
 */
@Mixin(AbuseReportContext.class)
public interface AbuseReportContextAccessor
{
    /**
     * Returns the reporter environment.
     *
     * @return environment
     */
    @Accessor
    ReporterEnvironment getEnvironment();
}
